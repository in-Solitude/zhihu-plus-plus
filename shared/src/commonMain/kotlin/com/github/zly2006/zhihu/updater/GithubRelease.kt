/*
 * Zhihu++ - Free & Ad-Free Zhihu client for all platforms.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.updater

import com.github.zly2006.zhihu.util.raiseForStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 项目仓库；检查更新、关于页、问题反馈和导出页脚都指向这里。 */
const val PROJECT_REPOSITORY_URL = "https://github.com/in-Solitude/zhihu-plus-plus"
const val PROJECT_LATEST_RELEASE_URL = "https://api.github.com/repos/in-Solitude/zhihu-plus-plus/releases/latest"
const val PROJECT_NIGHTLY_RELEASE_URL = "https://api.github.com/repos/in-Solitude/zhihu-plus-plus/releases/tags/nightly"

@Serializable
data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
data class GithubAsset(
    val name: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

fun extractGithubReleaseNotes(body: String): String = body
    .replace("\r\n", "\n")
    .substringAfter("## What's Changed\n")
    .substringBefore("\n**Full Changelog**:")
    .trimEnd('\n')

suspend fun fetchLatestZhihuRelease(
    client: HttpClient,
    githubToken: String?,
): GithubRelease = client
    .get(PROJECT_LATEST_RELEASE_URL) {
        githubToken?.let { token ->
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }.raiseForStatus()
    .body<GithubRelease>()

suspend fun fetchNightlyZhihuRelease(
    client: HttpClient,
    githubToken: String?,
): GithubRelease = client
    .get(PROJECT_NIGHTLY_RELEASE_URL) {
        githubToken?.let { token ->
            headers {
                append(HttpHeaders.Authorization, "Bearer $token")
            }
        }
    }.raiseForStatus()
    .body<GithubRelease>()

internal data class AndroidReleaseDownloadInfo(
    val browserDownloadUrl: String,
    val opensExternally: Boolean = false,
)

internal fun GithubRelease.extractAndroidDownloadInfo(isLiteVariant: Boolean): AndroidReleaseDownloadInfo {
    val apkAssets = assets.filter { it.contentType == "application/vnd.android.package-archive" }
    val variant = if (isLiteVariant) "lite" else "full"
    val selectedAsset = apkAssets.firstOrNull { it.name.contains(variant, ignoreCase = true) } ?: apkAssets.firstOrNull()
    if (selectedAsset != null) {
        return AndroidReleaseDownloadInfo(selectedAsset.browserDownloadUrl)
    }
    val quarkUrl = Regex("""https://pan\.quark\.cn/s/[A-Za-z0-9_-]+(?:\?[A-Za-z0-9._~%!$&*+,;=:@/?-]+)?""")
        .find(body.orEmpty())
        ?.value
        ?: error("发布中未找到 APK 或夸克网盘下载链接")
    return AndroidReleaseDownloadInfo(browserDownloadUrl = quarkUrl, opensExternally = true)
}
