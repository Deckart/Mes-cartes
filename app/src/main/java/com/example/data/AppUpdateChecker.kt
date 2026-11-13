package com.example.data

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
data class GitHubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url")
    val browserDownloadUrl: String = ""
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name")
    val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @SerialName("html_url")
    val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAssetDto> = emptyList()
)

data class AppUpdateInfo(
    val tagName: String,
    val versionName: String,
    val versionCode: Int?,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val hasDirectApkAsset: Boolean,
    val releasePageUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object MissingRepoConfig : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

class AppUpdateChecker(private val context: Context) {

    private val prefs = context.getSharedPreferences("mes_cartes_prefs", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun getConfiguredRepoSlug(): String? {
        val override = prefs.getString(KEY_REPO_OVERRIDE, null)?.let(::normalizeRepoSlug)
        if (!override.isNullOrBlank()) return override
        return normalizeRepoSlug(BuildConfig.GITHUB_REPO)
    }

    fun saveCustomRepoSlug(rawInput: String): String? {
        val normalized = normalizeRepoSlug(rawInput)
        prefs.edit().apply {
            if (normalized == null) remove(KEY_REPO_OVERRIDE)
            else putString(KEY_REPO_OVERRIDE, normalized)
        }.apply()
        return normalized
    }

    fun dismissUpdateTag(tagName: String) {
        prefs.edit().putString(KEY_DISMISSED_TAG, tagName).apply()
    }

    fun shouldAutoCheckNow(): Boolean {
        if (getConfiguredRepoSlug() == null) return false
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        return System.currentTimeMillis() - lastCheck >= AUTO_CHECK_INTERVAL_MS
    }

    suspend fun checkForUpdates(force: Boolean): UpdateCheckResult = withContext(Dispatchers.IO) {
        val repoSlug = getConfiguredRepoSlug() ?: return@withContext UpdateCheckResult.MissingRepoConfig

        if (!force && !shouldAutoCheckNow()) {
            return@withContext UpdateCheckResult.UpToDate
        }

        val url = "https://api.github.com/repos/$repoSlug/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "MesCartes-Android/${BuildConfig.VERSION_NAME}")
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                prefs.edit().putLong(KEY_LAST_CHECK_AT, System.currentTimeMillis()).apply()

                if (response.code == 404) {
                    return@use UpdateCheckResult.UpToDate
                }
                if (!response.isSuccessful) {
                    return@use UpdateCheckResult.Error(
                        "Impossible de vérifier les mises à jour (code ${response.code})."
                    )
                }
                val bodyString = response.body?.string().orEmpty()
                if (bodyString.isBlank()) {
                    return@use UpdateCheckResult.Error("Réponse vide du serveur de mise à jour.")
                }

                val dto = json.decodeFromString<GitHubReleaseDto>(bodyString)
                if (dto.draft || dto.prerelease) {
                    return@use UpdateCheckResult.UpToDate
                }

                val parsed = parseReleaseDto(
                    dto = dto,
                    currentVersionName = BuildConfig.VERSION_NAME,
                    currentVersionCode = BuildConfig.VERSION_CODE
                ) ?: return@use UpdateCheckResult.UpToDate

                val dismissedTag = prefs.getString(KEY_DISMISSED_TAG, null)
                if (!force && dismissedTag == parsed.tagName) {
                    return@use UpdateCheckResult.UpToDate
                }

                UpdateCheckResult.UpdateAvailable(parsed)
            }
        }.getOrElse { error ->
            UpdateCheckResult.Error(
                error.localizedMessage ?: "Connexion impossible pour vérifier les mises à jour."
            )
        }
    }

    companion object {
        private const val KEY_REPO_OVERRIDE = "github_repo_override"
        private const val KEY_LAST_CHECK_AT = "last_update_check_at"
        private const val KEY_DISMISSED_TAG = "dismissed_update_tag"
        private const val AUTO_CHECK_INTERVAL_MS = 3L * 60L * 60L * 1000L

        fun normalizeRepoSlug(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var cleaned = raw.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
            if (cleaned.startsWith("github.com/", ignoreCase = true)) {
                cleaned = cleaned.substring("github.com/".length)
            }
            cleaned = cleaned.removeSuffix(".git").trim('/')
            val parts = cleaned.split('/').filter { it.isNotBlank() }
            if (parts.size < 2) return null
            val owner = parts[0]
            val repo = parts[1]
            val validSegment = Regex("^[A-Za-z0-9_.-]+$")
            if (!owner.matches(validSegment) || !repo.matches(validSegment)) return null
            return "$owner/$repo"
        }

        fun parseReleaseTag(tagName: String): Pair<String, Int?> {
            val trimmed = tagName.trim().removePrefix("v").removePrefix("V")
            val buildRegex = Regex("^(.+?)(?:-build\\.|\\+build\\.|-)([0-9]+)$", RegexOption.IGNORE_CASE)
            val match = buildRegex.matchEntire(trimmed)
            return if (match != null) {
                val version = match.groupValues[1].trim()
                val buildCode = match.groupValues[2].toIntOrNull()
                version to buildCode
            } else {
                trimmed to null
            }
        }

        fun isRemoteVersionNewer(
            remoteVersionName: String,
            remoteVersionCode: Int?,
            currentVersionName: String,
            currentVersionCode: Int
        ): Boolean {
            if (remoteVersionCode != null && currentVersionCode > 0) {
                return remoteVersionCode > currentVersionCode
            }
            return compareSemver(remoteVersionName, currentVersionName) > 0
        }

        fun compareSemver(remote: String, current: String): Int {
            val remoteParts = remote.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
            val currentParts = current.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r != c) return r.compareTo(c)
            }
            return 0
        }

        fun parseReleaseDto(
            dto: GitHubReleaseDto,
            currentVersionName: String,
            currentVersionCode: Int
        ): AppUpdateInfo? {
            if (dto.tagName.isBlank()) return null
            val (versionName, versionCode) = parseReleaseTag(dto.tagName)
            if (!isRemoteVersionNewer(versionName, versionCode, currentVersionName, currentVersionCode)) {
                return null
            }
            val apkAsset = dto.assets.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) && it.browserDownloadUrl.isNotBlank()
            }
            val downloadUrl = apkAsset?.browserDownloadUrl ?: dto.htmlUrl
            if (downloadUrl.isBlank()) return null

            return AppUpdateInfo(
                tagName = dto.tagName,
                versionName = versionName.ifBlank { dto.tagName },
                versionCode = versionCode,
                releaseNotes = dto.body.trim(),
                apkDownloadUrl = downloadUrl,
                hasDirectApkAsset = apkAsset != null,
                releasePageUrl = dto.htmlUrl
            )
        }
    }
}
