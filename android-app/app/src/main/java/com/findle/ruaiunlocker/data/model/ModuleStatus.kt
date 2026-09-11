package com.findle.ruaiunlocker.data.model

enum class ModuleState {
    INSTALLED, NOT_INSTALLED, DISABLED
}

data class ModuleStatus(
    val state: ModuleState,
    val version: String,
    val versionCode: Int,
    val modulePath: String = "/data/adb/modules/unlocker_zrpb"
)
