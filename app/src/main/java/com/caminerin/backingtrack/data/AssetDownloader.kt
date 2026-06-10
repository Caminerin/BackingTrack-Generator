package com.caminerin.backingtrack.data

import android.content.Context
import com.caminerin.backingtrack.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads audio assets (Opus stems and 15 s previews) from a GitHub release
 * and caches them under filesDir/audio. Works for a private repo by sending a
 * read-only token (injected at build time, see [BuildConfig.ASSET_TOKEN]); for
 * a public repo the token can be empty.
 *
 * The GitHub asset endpoint answers with a 302 to a signed CDN URL. We follow
 * that redirect manually so the Authorization header is NOT forwarded to the
 * CDN (which would reject it).
 */
object AssetDownloader {

    private const val API = "https://api.github.com"
    // GitHub caps a release at 1000 assets, so audio is sharded across these
    // releases (fill order). The index merges all of them; later shards
    // override earlier ones if a name somehow repeats.
    private val TAGS = listOf("audio_opus", "audio_opus2", "audio_opus3")

    /** asset name -> (API asset url, public browser_download_url). */
    private class Asset(val apiUrl: String, val browserUrl: String)

    /** Lazily-loaded asset index. */
    @Volatile
    private var index: Map<String, Asset>? = null

    private fun audioDir(context: Context): File =
        File(context.filesDir, "audio").apply { mkdirs() }

    fun localFile(context: Context, name: String): File = File(audioDir(context), name)

    fun isCached(context: Context, name: String): Boolean {
        val f = localFile(context, name)
        return f.exists() && f.length() > 0
    }

    private fun applyAuth(conn: HttpURLConnection) {
        val token = BuildConfig.ASSET_TOKEN
        if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("User-Agent", "BackingTrackApp")
    }

    /** Loads (once) the merged asset list across all shard releases. */
    @Synchronized
    private fun assetIndex(): Map<String, Asset> {
        index?.let { return it }
        val map = HashMap<String, Asset>(2048)
        var anyFound = false
        for (tag in TAGS) {
            val relId = releaseIdForTag(tag) ?: continue
            anyFound = true
            addAssetsForRelease(relId, map)
        }
        if (!anyFound) throw IOException("No se pudo leer ningún release de audio")
        index = map
        return map
    }

    /** Resolves a release id from its tag, or null if the tag does not exist. */
    private fun releaseIdForTag(tag: String): Long? {
        val conn = (URL("$API/repos/${BuildConfig.ASSET_REPO}/releases/tags/$tag")
            .openConnection() as HttpURLConnection).apply {
            applyAuth(this)
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 20000
            readTimeout = 20000
        }
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body).getLong("id")
        } finally {
            conn.disconnect()
        }
    }

    /** Pages through a release's assets (100/page) and merges them into [map]. */
    private fun addAssetsForRelease(relId: Long, map: HashMap<String, Asset>) {
        var page = 1
        while (true) {
            val conn = (URL("$API/repos/${BuildConfig.ASSET_REPO}/releases/$relId/assets?per_page=100&page=$page")
                .openConnection() as HttpURLConnection).apply {
                applyAuth(this)
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 20000
                readTimeout = 20000
            }
            try {
                if (conn.responseCode != 200) return
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val assets = org.json.JSONArray(body)
                if (assets.length() == 0) return
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    map[a.getString("name")] =
                        Asset(a.getString("url"), a.optString("browser_download_url"))
                }
                if (assets.length() < 100) return
                page++
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Forces the asset index to be re-fetched on the next download. */
    fun invalidate() { index = null }

    /**
     * Ensures [name] exists locally, downloading it from the release if needed.
     * Blocking — call from a background dispatcher. [onProgress] is invoked with
     * 0..1 when the content length is known.
     */
    fun ensure(context: Context, name: String, onProgress: ((Float) -> Unit)? = null): File {
        val target = localFile(context, name)
        if (target.exists() && target.length() > 0) return target

        val asset = assetIndex()[name]
            ?: throw IOException("Asset no encontrado en el release: $name")

        // Public repo (no token): download the browser URL directly, no auth and
        // no API rate limit. Private repo (token set): use the authenticated API
        // asset endpoint and follow its 302 to the CDN manually.
        if (BuildConfig.ASSET_TOKEN.isEmpty() && asset.browserUrl.isNotEmpty()) {
            val cdn = (URL(asset.browserUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "BackingTrackApp")
                connectTimeout = 20000
                readTimeout = 60000
            }
            if (cdn.responseCode !in 200..299) {
                val c = cdn.responseCode
                cdn.disconnect()
                throw IOException("Descarga HTTP $c para $name")
            }
            writeStream(cdn.inputStream, target, cdn.contentLengthLong, onProgress) { cdn.disconnect() }
            return target
        }

        val conn = (URL(asset.apiUrl).openConnection() as HttpURLConnection).apply {
            applyAuth(this)
            setRequestProperty("Accept", "application/octet-stream")
            instanceFollowRedirects = false
            connectTimeout = 20000
            readTimeout = 60000
        }
        val code = conn.responseCode
        when (code) {
            in 300..399 -> {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrEmpty()) throw IOException("Redirección vacía para $name")
                // Download the signed CDN URL WITHOUT the Authorization header.
                val cdn = (URL(location).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "BackingTrackApp")
                    connectTimeout = 20000
                    readTimeout = 60000
                }
                if (cdn.responseCode !in 200..299) {
                    val c = cdn.responseCode
                    cdn.disconnect()
                    throw IOException("Descarga CDN HTTP $c para $name")
                }
                writeStream(cdn.inputStream, target, cdn.contentLengthLong, onProgress) { cdn.disconnect() }
            }
            in 200..299 -> {
                writeStream(conn.inputStream, target, conn.contentLengthLong, onProgress) { conn.disconnect() }
            }
            else -> {
                conn.disconnect()
                throw IOException("Descarga HTTP $code para $name")
            }
        }
        return target
    }

    private fun writeStream(
        input: InputStream,
        target: File,
        length: Long,
        onProgress: ((Float) -> Unit)?,
        close: () -> Unit,
    ) {
        val tmp = File(target.parentFile, target.name + ".part")
        try {
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    var read: Int
                    while (ins.read(buf).also { read = it } >= 0) {
                        out.write(buf, 0, read)
                        total += read
                        if (length > 0 && onProgress != null) {
                            onProgress((total.toFloat() / length).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            onProgress?.invoke(1f)
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            close()
        }
    }
}
