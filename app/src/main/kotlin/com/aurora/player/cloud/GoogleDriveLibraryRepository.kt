package com.aurora.player.cloud

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.aurora.player.domain.model.Track
import com.aurora.player.domain.model.TrackSource
import com.aurora.player.domain.repository.CloudLibraryRepository
import com.aurora.player.domain.util.TrackIdHasher
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

    // Zgłoszenie: "kliknięcie w konto w pickerze nic nie robi" — CAŁY ten flow dotąd połykał
    // każdy błąd w ciszy (patrz stare `catch (e: ApiException) { false }` niżej i
    // `.addOnFailureListener { continuation.resume(null) }`) — użytkownik nie miał ŻADNEGO
    // sposobu dowiedzieć się, na którym kroku i dlaczego się wysypało. Ten stan niesie
    // czytelny, techniczny opis ostatniego błędu (kod ApiException, gdy dostępny — np.
    // DEVELOPER_ERROR=10 oznacza niezgodność SHA-1/OAuth Client ID w Google Cloud Console,
    // NETWORK_ERROR=7 to brak sieci) do pokazania w UI zamiast martwej ciszy.
    private val _lastError = MutableStateFlow<String?>(null)
    override val lastError: StateFlow<String?> = _lastError

    override fun clearLastError() {
        _lastError.value = null
    }

    @Volatile
    private var cachedAccessToken: String? = null

    /**
     * Próbuje autoryzować w tle. Zwraca `null`, gdy się udało (albo się nie udało z powodu
     * błędu) — w obu przypadkach nie trzeba nic więcej robić; zwraca [IntentSender] tylko gdy
     * Google wymaga ekranu zgody, który wołający ma odpalić przez `ActivityResultLauncher`.
     */
    suspend fun connect(): IntentSender? {
        _lastError.value = null
        val result = awaitAuthorize() ?: return null
        if (result.hasResolution()) {
            val intentSender = result.pendingIntent?.intentSender
            if (intentSender == null) {
                // Bardzo rzadki, ale realny przypadek: Google mówi "trzeba zgody" (hasResolution
                // == true) ale nie dał nam jak jej zażądać — bez tego logowania to wyglądałoby
                // dokładnie jak zgłoszony bug ("nic się nie dzieje po kliknięciu ikony chmury").
                Log.e(TAG, "connect(): hasResolution=true ale pendingIntent == null")
                _lastError.value = "Google nie zwrócił ekranu zgody (pendingIntent=null)."
            }
            return intentSender
        }
        applyResult(result)
        return null
    }

    /** Wołane z callbacku `ActivityResultLauncher` po ekranie zgody Google. */
    fun handleAuthorizationResult(data: Intent?): Boolean {
        val intent = data ?: run {
            // To jest DOKŁADNIE zgłoszony bug: "kliknięcie w konto w pickerze nic nie robi" —
            // jeśli `data` (Intent z ActivityResult) jest null, to znaczy że sam system
            // account-picker/consent-screen zwrócił RESULT_CANCELED albo pustą odpowiedź, i
            // wcześniejszy kod po prostu wychodził tu cicho przez `return false` bez ŻADNEGO
            // śladu w logach — nie dało się odróżnić "user anulował" od "coś się wywaliło".
            Log.e(TAG, "handleAuthorizationResult(): result.data == null — Activity Result nie " +
                "przyniósł Intentu (user anulował ekran zgody, albo Google Play Services " +
                "zwróciło pustą odpowiedź bez wyjaśnienia)")
            _lastError.value = "Ekran logowania Google zamknął się bez wyniku (anulowano lub błąd Play Services)."
            return false
        }
        return try {
            val result = authorizationClient.getAuthorizationResultFromIntent(intent)
            applyResult(result)
            true
        } catch (e: ApiException) {
            // statusCode 10 = DEVELOPER_ERROR (niemal zawsze: SHA-1 użyty do podpisania tego
            // builda nie jest zarejestrowany w Google Cloud Console dla applicationId
            // com.aurora.player, albo w ogóle brak tam klienta OAuth typu "Android") —
            // to najbardziej prawdopodobna przyczyna tego zgłoszenia, teraz W KOŃCU widoczna.
            Log.e(TAG, "handleAuthorizationResult(): ApiException statusCode=${e.statusCode} " +
                "message=${e.message}", e)
            _lastError.value = "Błąd logowania Google (kod ${e.statusCode}): ${e.message}"
            false
        } catch (e: Exception) {
            // Etap 18→19: poprzedni kod łapał WYŁĄCZNIE ApiException — każdy inny wyjątek
            // (np. gdyby `toGoogleSignInAccount()` rzucił na jakiejś wersji play-services-auth)
            // przechodziłby NIEOBSŁUŻONY przez callback ActivityResultLaunchera, co w Compose
            // potrafi objawić się jako pozornie "nic się nie dzieje" zamiast czytelnego crasha.
            Log.e(TAG, "handleAuthorizationResult(): nieoczekiwany wyjątek ${e::class.simpleName}", e)
            _lastError.value = "Nieoczekiwany błąd logowania: ${e::class.simpleName} — ${e.message}"
            false
        }
    }

    private suspend fun awaitAuthorize(): AuthorizationResult? =
        suspendCancellableCoroutine { continuation ->
            authorizationClient.authorize(authorizationRequest)
                .addOnSuccessListener { result -> continuation.resume(result) }
                .addOnFailureListener { exception ->
                    val statusCode = (exception as? ApiException)?.statusCode
                    Log.e(TAG, "authorize() failure, statusCode=$statusCode", exception)
                    _lastError.value = if (statusCode != null) {
                        "Błąd autoryzacji Google (kod $statusCode): ${exception.message}"
                    } else {
                        "Błąd autoryzacji Google: ${exception.message}"
                    }
                    continuation.resume(null)
                }
        }

    private fun applyResult(result: AuthorizationResult) {
        cachedAccessToken = result.accessToken
        _accountEmail.value = try {
            result.toGoogleSignInAccount()?.email
        } catch (e: Exception) {
            Log.e(TAG, "toGoogleSignInAccount() rzucił wyjątek — kontynuuję bez adresu e-mail", e)
            null
        }
        _isSignedIn.value = result.accessToken != null
        if (result.accessToken == null) {
            Log.e(TAG, "applyResult(): result.accessToken == null mimo braku hasResolution — " +
                "autoryzacja 'się udała' ale nie dała tokenu")
            _lastError.value = "Google potwierdził logowanie, ale nie zwrócił tokenu dostępu."
        }
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
     * przez [TrackIdHasher] (patrz DESIGN.md Etap 13) zamiast wcześniejszego ręcznego
     * "offset + 31-bitowy String.hashCode()", który nie skalowałby się bezpiecznie na kolejne
     * źródła chmurowe z Etapu 12 — dyskryminator "google_drive" daje temu źródłu własną
     * przestrzeń skrótu, więc dowolna liczba przyszłych źródeł nie koliduje ze sobą nawzajem.
     */
    private fun cloudTrackId(driveFileId: String): Long =
        TrackIdHasher.deriveId(SOURCE_DISCRIMINATOR, driveFileId)

    private companion object {
        const val SOURCE_DISCRIMINATOR = "google_drive"
        const val TAG = "GoogleDriveLibraryRepo"
    }
}
