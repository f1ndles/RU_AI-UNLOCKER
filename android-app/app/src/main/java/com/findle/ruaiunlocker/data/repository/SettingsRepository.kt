package com.findle.ruaiunlocker.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val UPDATE_INTERVAL_HOURS = intPreferencesKey("update_interval_hours")
        val AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val MAX_BACKUPS = intPreferencesKey("max_backups")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LAST_KNOWN_SHA = stringPreferencesKey("last_known_sha")
        val LAST_KNOWN_DATE = stringPreferencesKey("last_known_date")
    }

    val updateIntervalFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.UPDATE_INTERVAL_HOURS] ?: 12
    }

    suspend fun setUpdateInterval(hours: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.UPDATE_INTERVAL_HOURS] = hours
        }
    }

    val autoUpdateFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.AUTO_UPDATE] ?: false
    }

    suspend fun setAutoUpdate(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AUTO_UPDATE] = enabled
        }
    }

    val maxBackupsFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[Keys.MAX_BACKUPS] ?: 5
    }

    suspend fun setMaxBackups(count: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.MAX_BACKUPS] = count
        }
    }

    val themeModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "dark"
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    val lastKnownShaFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_KNOWN_SHA] ?: ""
    }

    suspend fun setLastKnownSha(sha: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_KNOWN_SHA] = sha
        }
    }

    val lastKnownDateFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.LAST_KNOWN_DATE] ?: ""
    }

    suspend fun setLastKnownDate(date: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_KNOWN_DATE] = date
        }
    }
}
