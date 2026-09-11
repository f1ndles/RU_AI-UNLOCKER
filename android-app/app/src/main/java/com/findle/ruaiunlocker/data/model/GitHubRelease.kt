package com.findle.ruaiunlocker.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String = "",
    @Json(name = "name") val name: String = "",
    @Json(name = "body") val body: String = "",
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "assets") val assets: List<GitHubAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    @Json(name = "name") val name: String = "",
    @Json(name = "size") val size: Long = 0L,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = ""
)
