package com.aurora.player.cloud

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.repository.CloudLibraryRepository
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Biblioteka w chmurze przez Google Drive — patrz DESIGN.md, sekcja "Chmura". Użytkownik loguje
 * się własnym kontem Google i appka czyta pliki audio z jego Drive; appka NIE uploaduje niczego
 * (upload robi sam użytkownik przez appkę Google Drive) — patrz [refreshCloudTracks].
 *
 * Uwaga architektoniczna: klasyczne `GoogleSignInClient`/`GoogleSignInOptions`
 * (`com.google.android.gms.auth.api.signin.*`) zostały w międzyczasie CAŁKOWICIE USUNIĘTE
 * z `play-services-auth` (zweryfikowane rozpakowaniem .aar — build się wysypał na
 * "Unresolved reference" i to właśnie ujawniło). Zamiast tego używamy nowego
 * `Identity.getAuthorizationClient()` ("Authorization API") — flow jest dwuetapowy:
 * [connect] próbuje autoryzować bez UI; jeśli trzeba zgody użytkownika, zwraca [IntentSender]
 * do odpalenia przez `ActivityResultLauncher` (patrz LibraryScreen), a wynik wraca do
 * [handleAuthorizationResult].
 *
 * Natywne "Drive Android API" jest osobno deprecated przez Google na rzecz REST Drive API v3
 * (developers.google.com/drive/android/deprecation) — stąd `google-api-services-drive`, nie
 * `com.google.android.gms.drive.*`.
 */
@Singleton
class GoogleDriveLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : CloudLibraryRepository {

    private val authorizationClient: AuthorizationClient = Identity.getAuthorizationClient(context)

    private val authorizationRequest: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_READONLY)))
            .build()

    private val _isSignedIn = MutableStateFlow(false)
    override val isSignedIn: StateFlow<Boolean> = _isSignedIn

    private val _accountEmail = MutableStateFlow<String?>(null)
    override val accountEmail: StateFlow<String?> = _accountEmail

    @Volatile
    private var cachedAccessToken: String? = null

    /**
     * Próbuje autoryzować w tle. Zwraca `null`, gdy się udało (albo się nie udało z powodu
     * błędu) — w obu przypadkach nie trzeba nic więcej robić; zwraca [IntentSender] tylko gdy
     * Google wymaga ekranu zgody, który wołający ma odpalić przez `ActivityResultLauncher`.
     */
    suspend fun connect(): IntentSender? {
        val result = awaitAuthorize() ?: return null
        if (result.hasResolution()) {
            return result.pendingIntent?.intentSender
        }
        applyResult(result)
        return null
    }

    /** Wołane z callbacku `ActivityResultLauncher` po ekranie zgody Google. */
    fun handleAuthorizationResult(data: Intent?): Boolean {
        val intent = data ?: return false
        return try {
            val result = authorizationClient.getAuthorizationResultFromIntent(intent)
            applyResult(result)
            true
        } catch (e: ApiException) {
            false
        }
    }

    private suspend fun awaitAuthorize(): AuthorizationResult? =
        suspendCancellableCoroutine { continuation ->
            authorizationClient.authorize(authorizationRequest)
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { continuation.resume(null) }
        }

    private fun applyResult(result: AuthorizationResult) {
        cachedAccessToken = result.accessToken
        _accountEmail.value = result.toGoogleSignInAccount()?.email
        _isSignedIn.value = result.accessToken != null
    }

    override fun signOut() {
        cachedAccessToken = null
        _accountEmail.value = null
        _isSignedIn.value = false
    }

    override suspend fun refreshCloudTracks(): List<Track> = withContext(Dispatchers.IO) {
        val token = cachedAccessToken ?: return@withContext emptyList()

        val drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            HttpRequestInitializer { request -> request.headers.authorization = "Bearer $token" },
        ).setApplicationName("Aurora").build()

        val result = drive.files().list()
            .setQ("mimeType contains 'audio/' and trashed = false")
            .setFields("files(id, name, modifiedTime)")
            .setPageSize(1000)
            .execute()

        result.files.orEmpty().map { file ->
            Track(
                id = cloudTrackId(file.id),
                uri = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
                title = file.name.substringBeforeLast('.'),
                artist = accountEmail.value ?: "Google Drive",
                album = "Google Drive",
                genre = null,
                year = null,
                // Drive nie zwraca długości audio w metadanych — Media3 ją odkryje sam po
                // rozpoczęciu odtwarzania, patrz PlaybackState.durationMs.
                durationMs = 0L,
                dateAddedMs = file.modifiedTime?.value ?: 0L,
                albumArtUri = null,
                source = TrackSource.CLOUD,
            )
        }
    }

    /**
     * Blokujące — wołane wyłącznie z wątku ładowania Media3 (patrz [GoogleDriveDataSourceFactory]).
     * Autoryzuje na nowo (Play Services odświeża po cichu, bez UI, dopóki zgoda nie została
     * cofnięta) zamiast zwracać cache'owany token — dzięki temu każdy NOWY utwór z Drive dostaje
     * świeży token nawet po długiej sesji, gdy poprzedni już wygasł (tokeny żyją ~1h).
     */
    override fun currentAccessTokenBlocking(): String? {
        if (!_isSignedIn.value) return null
        return try {
            val result = Tasks.await(authorizationClient.authorize(authorizationRequest), 10, TimeUnit.SECONDS)
            if (result.hasResolution()) {
                // Zgoda została cofnięta w międzyczasie — trzeba przejść [connect] od nowa z UI.
                null
            } else {
                applyResult(result)
                result.accessToken
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Track.id jest Long w całej apce (Room, Genius) — Drive daje opaque String, więc mapujemy
     * przez stabilny (String.hashCode() jest częścią kontraktu języka, nie zmieni się) hash
     * przesunięty poza zakres realnych MediaStore._ID, żeby nie kolidować z lokalnymi utworami.
     * Ryzyko kolizji hashy jest teoretyczne przy realnej skali osobistej biblioteki w chmurze.
     */
    private fun cloudTrackId(driveFileId: String): Long =
        CLOUD_ID_OFFSET + (driveFileId.hashCode().toLong() and 0x7FFFFFFFL)

    private companion object {
        const val CLOUD_ID_OFFSET = 1_000_000_000_000L
    }
}
