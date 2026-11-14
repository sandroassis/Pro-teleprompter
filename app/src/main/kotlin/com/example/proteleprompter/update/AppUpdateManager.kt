package com.example.proteleprompter.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AppVersionInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String = DEFAULT_APK_URL,
    val browserDownloadUrl: String = DEFAULT_BROWSER_APK_URL,
    val releaseNotes: String = "",
    val latestCommitSha: String = "",
    val latestCommitMessage: String = ""
) {
    companion object {
        const val DEFAULT_VERSION_JSON_URL =
            "https://raw.githubusercontent.com/sandroassis/Pro-teleprompter/main/version.json"
        const val DEFAULT_GITHUB_COMMITS_API =
            "https://api.github.com/repos/sandroassis/Pro-teleprompter/commits/main"
        const val DEFAULT_APK_URL =
            "https://raw.githubusercontent.com/sandroassis/Pro-teleprompter/main/pro-teleprompter.apk"
        const val DEFAULT_BROWSER_APK_URL =
            "https://github.com/sandroassis/Pro-teleprompter/raw/main/pro-teleprompter.apk"
    }
}

sealed interface UpdateCheckResult {
    data class Success(
        val localVersion: AppVersionInfo,
        val remoteVersion: AppVersionInfo,
        val hasUpdate: Boolean
    ) : UpdateCheckResult

    data class Error(
        val localVersion: AppVersionInfo,
        val message: String
    ) : UpdateCheckResult
}

data class DownloadProgress(
    val fraction: Float,
    val downloadedBytes: Long,
    val totalBytes: Long
)

object AppUpdateManager {

    fun getInstalledVersion(context: Context): AppVersionInfo {
        var pkgVersionName = "1.0.0"
        var pkgVersionCode = 100
        try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgVersionName = pkgInfo.versionName ?: "1.0.0"
            pkgVersionCode = PackageInfoCompat.getLongVersionCode(pkgInfo).toInt()
        } catch (_: Exception) {
        }

        return try {
            val rawAsset = context.assets.open("version.json").bufferedReader().use { it.readText() }
            val json = JSONObject(rawAsset)
            val assetCode = json.optInt("versionCode", pkgVersionCode)
            val assetName = json.optString("versionName", pkgVersionName)
            AppVersionInfo(
                versionName = if (assetCode >= pkgVersionCode) assetName else pkgVersionName,
                versionCode = maxOf(assetCode, pkgVersionCode),
                apkUrl = json.optString("apkUrl", AppVersionInfo.DEFAULT_APK_URL),
                browserDownloadUrl = json.optString(
                    "browserDownloadUrl",
                    AppVersionInfo.DEFAULT_BROWSER_APK_URL
                ),
                releaseNotes = json.optString("releaseNotes", "")
            )
        } catch (_: Exception) {
            AppVersionInfo(
                versionName = pkgVersionName,
                versionCode = pkgVersionCode
            )
        }
    }

    suspend fun checkForUpdates(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        val localVersion = getInstalledVersion(context)
        try {
            val cacheBuster = System.currentTimeMillis()
            val versionUrl = URL("${AppVersionInfo.DEFAULT_VERSION_JSON_URL}?cb=$cacheBuster")
            val connection = (versionUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
                setRequestProperty("Pragma", "no-cache")
                setRequestProperty("User-Agent", "ProTeleprompter-Android-Updater")
            }

            var remoteVersionName = localVersion.versionName
            var remoteVersionCode = localVersion.versionCode
            var remoteApkUrl = AppVersionInfo.DEFAULT_APK_URL
            var remoteBrowserUrl = AppVersionInfo.DEFAULT_BROWSER_APK_URL
            var remoteNotes = ""

            if (connection.responseCode in 200..299) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                remoteVersionName = json.optString("versionName", localVersion.versionName)
                remoteVersionCode = json.optInt("versionCode", localVersion.versionCode)
                remoteApkUrl = json.optString("apkUrl", AppVersionInfo.DEFAULT_APK_URL)
                remoteBrowserUrl = json.optString(
                    "browserDownloadUrl",
                    AppVersionInfo.DEFAULT_BROWSER_APK_URL
                )
                remoteNotes = json.optString("releaseNotes", "")
            }
            connection.disconnect()

            // Also fetch latest commit metadata from GitHub API for real-time changelog
            var latestSha = ""
            var latestCommitMsg = ""
            try {
                val commitConn = (URL(AppVersionInfo.DEFAULT_GITHUB_COMMITS_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    useCaches = false
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "ProTeleprompter-Android-Updater")
                }
                if (commitConn.responseCode in 200..299) {
                    val commitBody = commitConn.inputStream.bufferedReader().use { it.readText() }
                    val commitJson = JSONObject(commitBody)
                    latestSha = commitJson.optString("sha", "").take(7)
                    val commitObj = commitJson.optJSONObject("commit")
                    latestCommitMsg = commitObj?.optString("message", "")
                        ?.lines()
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                }
                commitConn.disconnect()
            } catch (_: Exception) {
            }

            val remoteInfo = AppVersionInfo(
                versionName = remoteVersionName,
                versionCode = remoteVersionCode,
                apkUrl = remoteApkUrl,
                browserDownloadUrl = remoteBrowserUrl,
                releaseNotes = remoteNotes,
                latestCommitSha = latestSha,
                latestCommitMessage = latestCommitMsg
            )

            val hasUpdate = remoteInfo.versionCode > localVersion.versionCode
            UpdateCheckResult.Success(
                localVersion = localVersion,
                remoteVersion = remoteInfo,
                hasUpdate = hasUpdate
            )
        } catch (e: Exception) {
            UpdateCheckResult.Error(
                localVersion = localVersion,
                message = "Não foi possível conectar ao servidor de atualizações (${e.localizedMessage ?: "verifique sua internet"})."
            )
        }
    }

    suspend fun downloadUpdateApk(
        context: Context,
        apkUrl: String = AppVersionInfo.DEFAULT_APK_URL,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }
            val targetFile = File(updatesDir, "pro-teleprompter-update.apk")
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val cacheBuster = System.currentTimeMillis()
            val urlWithBust = if (apkUrl.contains("?")) "$apkUrl&cb=$cacheBuster" else "$apkUrl?cb=$cacheBuster"
            val connection = (URL(urlWithBust).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "ProTeleprompter-Android-Updater")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                return@withContext Result.failure(
                    IllegalStateException("Servidor retornou código HTTP $responseCode ao baixar o APK.")
                )
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val fraction = if (totalBytes > 0L) {
                            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        } else {
                            -1f
                        }
                        onProgress(
                            DownloadProgress(
                                fraction = fraction,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes
                            )
                        )
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (!targetFile.exists() || targetFile.length() < 100_000L) {
                return@withContext Result.failure(
                    IllegalStateException("O arquivo APK baixado está incompleto ou corrompido.")
                )
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun canInstallApkDirectly(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallUnknownAppsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File): Result<Unit> {
        return try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openBrowserDownloadFallback(
        context: Context,
        browserUrl: String = AppVersionInfo.DEFAULT_BROWSER_APK_URL
    ) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(browserUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }
}
