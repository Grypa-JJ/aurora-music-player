package com.aurora.player.cloud

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Etap 12/22 (DESIGN.md) — zastępuje poprzedni `GoogleDriveDataSourceFactory`, który ZAWSZE
 * dokładał nagłówek `Authorization: Bearer <token Google>` do KAŻDEGO żądania https://,
 * niezależnie od tego, jaki serwer był po drugiej stronie. To działało, dopóki istniało tylko
 * jedno źródło https:// (Google Drive) — z dojściem NAS/WebDAV (inny serwer, inny typ
 * autoryzacji: Basic Auth zamiast Bearer) trzeba dopasowywać nagłówek do KONKRETNEGO żądania,
 * nie do całej fabryki na raz.
 *
 * `DataSource.Factory.createDataSource()` jest wołane PRZED tym, jak Media3 zna URI żądania
 * (URI poznaje dopiero `open(dataSpec)`) — więc decyzja "jaki nagłówek" musi zapaść WEWNĄTRZ
 * `open()`, nie przy tworzeniu fabryki. Stąd cienki wrapper delegujący do jednego
 * `DefaultHttpDataSource`, ale ustawiający właściwy `Authorization` tuż przed `open()`.
 */
@UnstableApi
class AuthenticatingHttpDataSourceFactory(
    private val getGoogleAccessToken: () -> String?,
    private val getWebDavAuthHeader: (Uri) -> String?,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        AuthenticatingHttpDataSource(getGoogleAccessToken, getWebDavAuthHeader)
}

@UnstableApi
private class AuthenticatingHttpDataSource(
    private val getGoogleAccessToken: () -> String?,
    private val getWebDavAuthHeader: (Uri) -> String?,
) : DataSource {
    private val delegate: HttpDataSource = DefaultHttpDataSource.Factory().createDataSource()

    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val authHeader = if (uri.host == GOOGLE_DRIVE_HOST) {
            getGoogleAccessToken()?.let { "Bearer $it" }
        } else {
            getWebDavAuthHeader(uri)
        }
        if (authHeader != null) {
            delegate.setRequestProperty("Authorization", authHeader)
        }
        return delegate.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate.read(buffer, offset, length)

    override fun addTransferListener(transferListener: TransferListener) =
        delegate.addTransferListener(transferListener)

    override fun getUri(): Uri? = delegate.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders

    override fun close() = delegate.close()

    private companion object {
        const val GOOGLE_DRIVE_HOST = "www.googleapis.com"
    }
}
