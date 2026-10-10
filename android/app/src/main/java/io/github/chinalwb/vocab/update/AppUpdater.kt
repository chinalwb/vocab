package io.github.chinalwb.vocab.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查 App 更新: the APK committed at android/apk/VoCab.apk on main is the latest build, and
 * version.json next to it says which version that is (Gradle's publishApk writes both). The
 * download goes to the cache and is handed to the system installer, which asks once for
 * "install unknown apps". Same debug-key signature, so it installs over the current app.
 */
@Serializable
data class RemoteVersion(val versionCode: Int, val versionName: String)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val remote: RemoteVersion) : AppUpdateState
    data class Downloading(val remote: RemoteVersion, val progress: Float) : AppUpdateState
}

class AppUpdater(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(): RemoteVersion = withContext(Dispatchers.IO) {
        // the query string gets past raw.githubusercontent's few-minute cache
        val text = open("$BASE/version.json?t=${System.currentTimeMillis()}").inputStream.bufferedReader().use { it.readText() }
        json.decodeFromString<RemoteVersion>(text)
    }

    suspend fun download(onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val conn = open("$BASE/VoCab.apk?t=${System.currentTimeMillis()}")
        val total = conn.contentLengthLong
        val file = File(context.cacheDir, "update/VoCab.apk").apply { parentFile?.mkdirs() }
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        file
    }

    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
    }

    companion object {
        const val BASE = "https://raw.githubusercontent.com/chinalwb/vocab/main/android/apk"
    }
}
