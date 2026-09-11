package com.findle.ruaiunlocker.data.api

import com.findle.ruaiunlocker.data.model.GitHubCommit
import com.findle.ruaiunlocker.data.model.GitHubRelease
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GitHubApiService {

    @GET("repos/ImMALWARE/dns.malw.link/commits")
    suspend fun getHostsCommits(
        @Query("path") path: String = "hosts",
        @Query("per_page") perPage: Int = 1
    ): List<GitHubCommit>

    @GET("repos/f1ndles/RU_AI-UNLOCKER/releases/latest")
    suspend fun getLatestAppRelease(): Response<GitHubRelease>

    companion object {
        const val BASE_URL = "https://api.github.com/"
        const val HOSTS_RAW_URL = "https://raw.githubusercontent.com/ImMALWARE/dns.malw.link/refs/heads/master/hosts"
    }
}
