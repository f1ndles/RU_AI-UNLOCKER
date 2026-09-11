package com.findle.ruaiunlocker.data.model

data class HostsInfo(
    val lastUpdate: String,
    val entriesCount: Int,
    val fileSize: Long,
    val entries: List<HostEntry> = emptyList()
)

data class HostEntry(
    val ip: String,
    val domain: String,
    val section: String = ""
)
