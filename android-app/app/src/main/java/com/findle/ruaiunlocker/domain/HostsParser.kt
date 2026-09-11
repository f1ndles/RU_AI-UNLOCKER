package com.findle.ruaiunlocker.domain

import com.findle.ruaiunlocker.data.model.HostEntry
import com.findle.ruaiunlocker.data.model.HostsInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HostsParser @Inject constructor() {

    fun parseHostsContent(content: String): HostsInfo {
        var lastUpdate = ""
        val entries = mutableListOf<HostEntry>()
        var currentSection = ""

        content.lines().forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.contains("Последнее обновление", ignoreCase = true) -> {
                    lastUpdate = trimmed.substringAfter(":").trim()
                }
                trimmed.startsWith("# ") && !trimmed.startsWith("##") -> {
                    currentSection = trimmed.removePrefix("# ").trim()
                }
                trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                    val parts = trimmed.split("\\s+".toRegex(), limit = 2)
                    if (parts.size == 2) {
                        entries.add(
                            HostEntry(
                                ip = parts[0],
                                domain = parts[1],
                                section = currentSection
                            )
                        )
                    }
                }
            }
        }

        return HostsInfo(
            lastUpdate = lastUpdate,
            entriesCount = entries.size,
            fileSize = content.toByteArray().size.toLong(),
            entries = entries
        )
    }

    fun formatHostEntry(ip: String, domain: String): String {
        return "$ip $domain"
    }

    fun addEntry(content: String, ip: String, domain: String): String {
        return content.trimEnd() + "\n" + formatHostEntry(ip, domain) + "\n"
    }

    fun removeEntry(content: String, domain: String): String {
        return content.lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.isNotEmpty() && !trimmed.startsWith("#") &&
                    trimmed.split("\\s+".toRegex(), limit = 2).getOrNull(1) == domain
            }
            .joinToString("\n")
    }
}
