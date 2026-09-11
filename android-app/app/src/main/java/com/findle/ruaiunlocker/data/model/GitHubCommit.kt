package com.findle.ruaiunlocker.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GitHubCommit(
    @Json(name = "sha") val sha: String,
    @Json(name = "commit") val commit: CommitDetail
)

@JsonClass(generateAdapter = true)
data class CommitDetail(
    @Json(name = "author") val author: CommitAuthor,
    @Json(name = "message") val message: String
)

@JsonClass(generateAdapter = true)
data class CommitAuthor(
    @Json(name = "name") val name: String,
    @Json(name = "date") val date: String
)
