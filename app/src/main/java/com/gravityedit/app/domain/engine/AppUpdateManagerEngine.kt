package com.gravityedit.app.domain.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * Distribution channel detected from the OS `PackageManager` install source.
 *
 * To comply with Google Play Developer Program Policies (Device & Network Abuse / Section 4.5)
 * and Google Play Protect requirements:
 * - Builds installed from Google Play (`GOOGLE_PLAY_STORE`) NEVER download or self-install APKs
 *   from external servers; they route exclusively through Google Play's official update channel.
 * - Builds installed directly / sideloaded (`GITHUB_HTTPS_PACKAGE_INSTALLER`) fetch release metadata
 *   over HTTPS (TLS), verify the APK header & SHA-256 cryptographic digest in a private cache folder,
 *   and hand off installation interactively to the system `PackageInstaller` via a scoped `FileProvider`
 *   so Google Play Protect can scan and verify the package signature.
 */
enum class UpdateDistributionChannel(
    val displayName: String,
    val policyDescription: String
) {
    GOOGLE_PLAY_STORE(
        displayName = "Google Play Store (Play In-App Updates)",
        policyDescription = "Managed by Google Play Store & Play Protect. External APK downloads are disabled per Google Play Policy."
    ),
    GITHUB_HTTPS_PACKAGE_INSTALLER(
        displayName = "GitHub Official HTTPS + System PackageInstaller",
        policyDescription = "HTTPS TLS release check + SHA-256 digest verification + user-consented Android PackageInstaller handoff scanned by Google Play Protect."
    )
}

enum class AppUpdateCheckStatus {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR
}

data class AppReleaseInfo(
    val versionName: String,
    val tagName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val publishedAt: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?,
    val apkSizeBytes: Long,
    val expectedSha256: String?,
    val isPreRelease: Boolean
)

/**
 * Google Play Protect–compatible Auto-Update Engine for GravityEdit.
 */
object AppUpdateManagerEngine {

    const val GITHUB_REPO_OWNER = "petrosvassiliou"
    const val GITHUB_REPO_NAME = "GravityEdit"
    const val GITHUB_RELEASES_LATEST_API =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases/latest"
    const val GITHUB_RELEASES_LIST_API =
        "https://api.github.com/repos/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases?per_page=10"
    const val GITHUB_RELEASES_WEB_URL =
        "https://github.com/$GITHUB_REPO_OWNER/$GITHUB_REPO_NAME/releases"

    private val PLAY_STORE_INSTALLERS = setOf(
        "com.android.vending",
        "com.google.android.feedback"
    )

    /**
     * Detects whether the app was installed via Google Play Store or via direct/GitHub APK installation.
     */
    fun detectDistributionChannel(context: Context): UpdateDistributionChannel {
        val installerPackage: String? = runCatching {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        }.getOrNull()

        return if (installerPackage != null && installerPackage in PLAY_STORE_INSTALLERS) {
            UpdateDistributionChannel.GOOGLE_PLAY_STORE
        } else {
            UpdateDistributionChannel.GITHUB_HTTPS_PACKAGE_INSTALLER
        }
    }

    /**
     * Normalizes a version string such as `v1.0.0-debug` or `v1.2.3` into a clean `[major, minor, patch]` list.
     */
    fun parseSemVerParts(version: String): List<Int> {
        val cleaned = version
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        return cleaned.split('.')
            .map { part -> part.filter { it.isDigit() }.toIntOrNull() ?: 0 }
            .let { parts ->
                when {
                    parts.size >= 3 -> parts.take(3)
                    parts.size == 2 -> parts + listOf(0)
                    parts.size == 1 -> parts + listOf(0, 0)
                    else -> listOf(0, 0, 0)
                }
            }
    }

    /**
     * Compares two semantic versions.
     * Returns `> 0` if [remoteVersion] is newer than [currentVersion], `0` if equal, `< 0` if older.
     */
    fun compareSemVer(currentVersion: String, remoteVersion: String): Int {
        val current = parseSemVerParts(currentVersion)
        val remote = parseSemVerParts(remoteVersion)
        for (i in 0 until 3) {
            val cmp = remote[i].compareTo(current[i])
            if (cmp != 0) return cmp
        }
        return 0
    }

    fun isRemoteVersionNewer(currentVersion: String, remoteVersion: String): Boolean {
        return compareSemVer(currentVersion, remoteVersion) > 0
    }

    /**
     * Parses a GitHub Release JSON object into [AppReleaseInfo].
     */
    fun parseReleaseJson(json: JSONObject): AppReleaseInfo {
        val tagName = json.optString("tag_name", "v1.0.0").ifBlank { "v1.0.0" }
        val versionName = tagName.removePrefix("v").removePrefix("V")
        val releaseTitle = json.optString("name", tagName).ifBlank { tagName }
        val body = json.optString("body", "").ifBlank { "Bug fixes and performance improvements." }
        val publishedAt = json.optString("published_at", "").take(10)
        val htmlUrl = json.optString("html_url", GITHUB_RELEASES_WEB_URL).ifBlank { GITHUB_RELEASES_WEB_URL }
        val isPreRelease = json.optBoolean("prerelease", false)

        var apkUrl: String? = null
        var apkSize = 0L
        val assets = json.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val downloadUrl = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.startsWith("https://")) {
                apkUrl = downloadUrl
                apkSize = asset.optLong("size", 0L)
                break
            }
        }

        // Extract optional SHA-256 checksum if documented in release notes (e.g. "SHA-256: <64-hex>")
        val sha256Regex = Regex("""(?i)sha-?256[:\s`]*([a-f0-9]{64})""")
        val expectedSha256 = sha256Regex.find(body)?.groupValues?.getOrNull(1)?.lowercase()

        return AppReleaseInfo(
            versionName = versionName,
            tagName = tagName,
            releaseTitle = releaseTitle,
            releaseNotes = body,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl,
            apkSizeBytes = apkSize,
            expectedSha256 = expectedSha256,
            isPreRelease = isPreRelease
        )
    }

    /**
     * Queries the official GitHub Releases HTTPS endpoint to check for a newer release.
     */
    suspend fun fetchLatestRelease(includePreReleases: Boolean = false): Result<AppReleaseInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                val endpoint = if (includePreReleases) GITHUB_RELEASES_LIST_API else GITHUB_RELEASES_LATEST_API
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpsURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "GravityEdit-Android-UpdateChecker")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTPS update check returned HTTP $code")
                    }
                    val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    if (includePreReleases) {
                        val array = JSONArray(responseText)
                        if (array.length() == 0) {
                            throw IllegalStateException("No releases found in repository")
                        }
                        parseReleaseJson(array.getJSONObject(0))
                    } else {
                        parseReleaseJson(JSONObject(responseText))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

    /**
     * Downloads the signed APK over HTTPS into the private `context.cacheDir/updates` directory,
     * verifies that it is a valid APK archive and matches any expected SHA-256 digest,
     * and returns the verified [File] plus its computed SHA-256 hex digest.
     */
    suspend fun downloadAndVerifyReleaseApk(
        context: Context,
        release: AppReleaseInfo,
        onProgressPercent: (Int) -> Unit
    ): Result<Pair<File, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val downloadUrl = release.apkDownloadUrl
                ?: throw IllegalStateException("No HTTPS APK asset attached to release ${release.tagName}")
            require(downloadUrl.startsWith("https://")) {
                "Play Protect policy requires HTTPS transport for APK updates."
            }

            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            // Clean up old cached update files
            updatesDir.listFiles()?.forEach { it.delete() }

            val sanitizedTag = release.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val targetFile = File(updatesDir, "GravityEdit-$sanitizedTag.apk")

            val conn = (URL(downloadUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "GravityEdit-Android-UpdateChecker")
            }

            try {
                val status = conn.responseCode
                if (status !in 200..299) {
                    throw IllegalStateException("APK download failed with HTTP $status")
                }
                val totalBytes = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSizeBytes
                var downloadedBytes = 0L

                BufferedInputStream(conn.inputStream).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var read: Int
                        var lastReportedPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (pct != lastReportedPercent) {
                                    lastReportedPercent = pct
                                    onProgressPercent(pct)
                                }
                            }
                        }
                        output.flush()
                    }
                }
            } finally {
                conn.disconnect()
            }

            // Verify APK ZIP magic header (PK\x03\x04)
            if (!hasValidApkZipHeader(targetFile)) {
                targetFile.delete()
                throw IllegalStateException("Downloaded file is not a valid Android APK package.")
            }

            // Compute SHA-256 cryptographic digest
            val computedSha256 = computeSha256Hex(targetFile)
            val expected = release.expectedSha256
            if (!expected.isNullOrBlank() && !computedSha256.equals(expected, ignoreCase = true)) {
                targetFile.delete()
                throw SecurityException(
                    "SHA-256 integrity mismatch! Expected $expected but got $computedSha256."
                )
            }

            onProgressPercent(100)
            targetFile to computedSha256
        }
    }

    /**
     * Checks whether the file begins with the standard ZIP/APK local file header `PK\x03\x04`.
     */
    fun hasValidApkZipHeader(file: File): Boolean {
        if (!file.exists() || file.length() < 4L) return false
        return runCatching {
            FileInputStream(file).use { fis ->
                val header = ByteArray(4)
                val read = fis.read(header)
                read == 4 &&
                    header[0] == 0x50.toByte() &&
                    header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() &&
                    header[3] == 0x04.toByte()
            }
        }.getOrDefault(false)
    }

    /**
     * Computes the lowercase 64-character hex SHA-256 digest of [file] or [bytes].
     */
    fun computeSha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(16 * 1024)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Launches the Google Play Protect–scanned system `PackageInstaller` (`ACTION_VIEW`) using
     * a scoped `FileProvider` URI, or opens the system permission screen if the user has not yet
     * granted `REQUEST_INSTALL_PACKAGES` for this app.
     */
    fun launchPlayProtectCompliantUpdate(context: Context, apkFile: File): Result<Unit> {
        return runCatching {
            val channel = detectDistributionChannel(context)
            if (channel == UpdateDistributionChannel.GOOGLE_PLAY_STORE) {
                launchGooglePlayStorePage(context)
                return@runCatching
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return@runCatching
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }
            context.startActivity(installIntent)
        }
    }

    /**
     * Opens the official Google Play Store listing or GitHub Release page.
     */
    fun launchGooglePlayStorePage(context: Context) {
        val basePkg = context.packageName.removeSuffix(".debug")
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$basePkg")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(marketIntent)
        }.onFailure {
            openUrlInBrowser(context, GITHUB_RELEASES_WEB_URL)
        }
    }

    fun openUrlInBrowser(context: Context, httpsUrl: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(httpsUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
