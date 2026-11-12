package com.gravityedit.app.domain.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * Distribution channel detected from the OS `PackageManager` install source.
 *
 * To comply 100% with Google Play Developer Program Policies (Device & Network Abuse)
 * and Google Play Protect real-time security scanning:
 * - GravityEdit declares ZERO package-installation permissions (`REQUEST_INSTALL_PACKAGES` is absent)
 *   and NEVER downloads or self-installs `.apk` binaries inside the app process.
 * - Builds installed from Google Play (`GOOGLE_PLAY_STORE`) route strictly to Google Play Store.
 * - Builds installed directly (`GITHUB_HTTPS_BROWSER_HANDOFF`) check read-only JSON release metadata
 *   over HTTPS (TLS) and hand off any user-requested update URL to the trusted system browser /
 *   system DownloadManager so Google Play Protect never sees dropper-like behavior.
 */
enum class UpdateDistributionChannel(
    val displayName: String,
    val policyDescription: String
) {
    GOOGLE_PLAY_STORE(
        displayName = "Google Play Store (Play In-App Updates)",
        policyDescription = "Managed by Google Play Store & Play Protect. External downloads are disabled per Google Play Policy."
    ),
    GITHUB_HTTPS_BROWSER_HANDOFF(
        displayName = "GitHub Official HTTPS + Trusted System Browser",
        policyDescription = "Zero-permission read-only HTTPS release check + SHA-256 digest display + trusted system browser handoff (no in-app APK installer permissions)."
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
 * Google Play Protect–clean Update Metadata Engine for GravityEdit.
 * Performs read-only JSON version checks and delegates downloads exclusively to Google Play or the system browser.
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
     * Detects whether the app was installed via Google Play Store or via direct GitHub installation.
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
            UpdateDistributionChannel.GITHUB_HTTPS_BROWSER_HANDOFF
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

        var downloadUrl: String? = null
        var assetSize = 0L
        val assets = json.optJSONArray("assets") ?: JSONArray()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val browserUrl = asset.optString("browser_download_url", "")
            if (name.startsWith("GravityEdit-", ignoreCase = true) &&
                !name.endsWith(".sha256", ignoreCase = true) &&
                browserUrl.startsWith("https://")
            ) {
                downloadUrl = browserUrl
                assetSize = asset.optLong("size", 0L)
                break
            }
        }

        val sha256Regex = Regex("""(?i)sha-?256[:\s`]*([a-f0-9]{64})""")
        val expectedSha256 = sha256Regex.find(body)?.groupValues?.getOrNull(1)?.lowercase()

        return AppReleaseInfo(
            versionName = versionName,
            tagName = tagName,
            releaseTitle = releaseTitle,
            releaseNotes = body,
            publishedAt = publishedAt,
            htmlUrl = htmlUrl,
            apkDownloadUrl = downloadUrl,
            apkSizeBytes = assetSize,
            expectedSha256 = expectedSha256,
            isPreRelease = isPreRelease
        )
    }

    /**
     * Queries the official GitHub Releases HTTPS endpoint to check read-only JSON release metadata.
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
                    setRequestProperty("User-Agent", "GravityEdit-VersionChecker")
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
     * Computes the lowercase 64-character hex SHA-256 digest of [bytes].
     */
    fun computeSha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    /**
     * Opens Google Play Store (when installed from Google Play) or hands off the official HTTPS
     * release URL to the user's trusted system browser — zero `REQUEST_INSTALL_PACKAGES` required.
     */
    fun launchSystemVerifiedUpdate(context: Context, release: AppReleaseInfo?): Result<Unit> {
        return runCatching {
            val channel = detectDistributionChannel(context)
            if (channel == UpdateDistributionChannel.GOOGLE_PLAY_STORE) {
                launchGooglePlayStorePage(context)
                return@runCatching
            }
            val targetHttpsUrl = release?.apkDownloadUrl?.takeIf { it.startsWith("https://") }
                ?: release?.htmlUrl?.takeIf { it.startsWith("https://") }
                ?: GITHUB_RELEASES_WEB_URL
            openUrlInBrowser(context, targetHttpsUrl)
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
        require(httpsUrl.startsWith("https://")) { "HTTPS URL required" }
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(httpsUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            context.startActivity(intent)
        }
    }
}
