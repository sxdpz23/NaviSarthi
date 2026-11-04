package com.example.navisarthi.data

import android.content.Context
import androidx.work.*
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

internal object DriveBackup {
    private const val BASE = "https://www.googleapis.com/drive/v3"
    private const val FOLDER = "application/vnd.google-apps.folder"
    private const val WORK = "navisarthi-drive-backup"
    private const val LIMIT = 64 * 1024 * 1024
    data class Snapshot(val id: String, val name: String)

    fun preferences(context: Context) = context.getSharedPreferences("drive_backup", Context.MODE_PRIVATE)

    suspend fun authorize(context: Context) = Identity.getAuthorizationClient(context)
        .authorize(AuthorizationRequest.builder().setRequestedScopes(
            listOf(Scope("https://www.googleapis.com/auth/drive.file"))
        ).build()).await()

    suspend fun link(context: Context, token: String) = withContext(Dispatchers.IO) {
        val owner = owner(token)
        preferences(context).edit().putString("owner", owner.getString("permissionId"))
            .putString("email", owner.optString("emailAddress")).apply()
    }

    private fun owner(token: String) =
        request(token, "$BASE/about?fields=user(permissionId,emailAddress)").getJSONObject("user")

    private fun checkOwner(context: Context, token: String) {
        check(owner(token).getString("permissionId") == preferences(context).getString("owner", null)) {
            "Reconnect the Google account used for backup."
        }
    }

    fun setDaily(context: Context, enabled: Boolean) {
        preferences(context).edit().putBoolean("daily", enabled).apply()
        val manager = WorkManager.getInstance(context)
        if (enabled) {
            val work = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, work)
        } else manager.cancelUniqueWork(WORK)
    }

    fun disconnect(context: Context) {
        setDaily(context, false)
        preferences(context).edit().clear().apply()
    }

    suspend fun backup(context: Context, token: String) = withContext(Dispatchers.IO) {
        checkOwner(context, token)
        val folder = folder(token, create = true) ?: error("Drive folder unavailable")
        val snapshot = DriveSnapshot.capture(context).toString().toByteArray(Charsets.UTF_8)
        require(snapshot.size <= LIMIT) { "Backup exceeds 64 MB" }
        val name = "backup-${Instant.now().toString().replace(':', '-')}.json"
        val metadata = JSONObject().put("name", name)
            .put("parents", org.json.JSONArray(listOf(folder)))
            .put("mimeType", "application/json")
        val boundary = "navisarthi-${java.util.UUID.randomUUID()}"
        request(token, "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart",
            "POST", "multipart/related; boundary=$boundary") { stream ->
            stream.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n".toByteArray())
            stream.write("--$boundary\r\nContent-Type: application/json\r\n\r\n".toByteArray())
            stream.write(snapshot)
            stream.write("\r\n--$boundary--\r\n".toByteArray())
        }
        preferences(context).edit().putLong("lastBackup", System.currentTimeMillis()).remove("paused").apply()
        name
    }

    suspend fun list(context: Context, token: String): List<Snapshot> = withContext(Dispatchers.IO) {
        checkOwner(context, token)
        val folder = folder(token, create = false) ?: return@withContext emptyList()
        files(token, "'$folder' in parents and mimeType='application/json' and trashed=false")
            .filter { it.getString("name").startsWith("backup-") && it.getString("name").endsWith(".json") }
            .map { Snapshot(it.getString("id"), it.getString("name")) }
    }

    suspend fun restore(context: Context, token: String, snapshot: Snapshot) = withContext(Dispatchers.IO) {
        checkOwner(context, token)
        require(snapshot.id.matches(Regex("[A-Za-z0-9_-]+")))
        val body = request(token, "$BASE/files/${snapshot.id}?alt=media")
        DriveSnapshot.restore(context, body)
    }

    private fun folder(token: String, create: Boolean): String? {
        val matches = files(token, "'root' in parents and name='NaviSarthi' and mimeType='$FOLDER' and trashed=false")
        check(matches.size <= 1) { "Multiple accessible NaviSarthi folders. Rename duplicates in Drive." }
        return matches.firstOrNull()?.getString("id") ?: if (create) {
            val metadata = JSONObject().put("name", "NaviSarthi").put("mimeType", FOLDER)
            request(token, "$BASE/files", "POST", "application/json") { it.write(metadata.toString().toByteArray()) }
                .getString("id")
        } else null
    }

    private fun files(token: String, query: String): List<JSONObject> = buildList {
        var page: String? = null
        do {
            val suffix = page?.let { "&pageToken=${encode(it)}" }.orEmpty()
            val response = request(token, "$BASE/files?q=${encode(query)}&fields=nextPageToken,files(id,name)&orderBy=createdTime%20desc$suffix")
            val files = response.getJSONArray("files")
            for (index in 0 until files.length()) add(files.getJSONObject(index))
            page = response.optString("nextPageToken").ifBlank { null }
        } while (page != null)
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun request(
        token: String,
        url: String,
        method: String = "GET",
        contentType: String? = null,
        body: ((java.io.OutputStream) -> Unit)? = null,
    ): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setChunkedStreamingMode(8192)
                connection.outputStream.use(body)
            }
            val status = connection.responseCode
            if (status !in 200..299) throw DriveRequestFailure(status)
            val bytes = connection.inputStream.use { it.readNBytes(LIMIT + 1) }
            require(bytes.size <= LIMIT) { "Drive response exceeds 64 MB" }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}

internal class DriveRequestFailure(val status: Int) : java.io.IOException("Drive returned HTTP $status")

class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = DriveBackup.preferences(applicationContext)
        if (!prefs.getBoolean("daily", false) || !prefs.contains("owner")) return Result.success()
        return try {
            val auth = DriveBackup.authorize(applicationContext)
            val token = auth.accessToken
            if (auth.hasResolution() || token == null) {
                prefs.edit().putBoolean("paused", true).apply()
            } else {
                DriveBackup.backup(applicationContext, token)
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3 && (e !is DriveRequestFailure || e.status == 429 || e.status >= 500))
                Result.retry()
            else {
                prefs.edit().putBoolean("paused", true).apply()
                Result.success()
            }
        }
    }
}
