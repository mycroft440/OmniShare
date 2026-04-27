package com.omnishare

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    companion object {
        val AUTO_RESTART_MAX = intPreferencesKey("auto_restart_max")
        val AUTO_RESTART_NETWORK_CHANGE = booleanPreferencesKey("auto_restart_network_change")
        val SHOW_SPEED = booleanPreferencesKey("show_speed")
        val SHOW_PING = booleanPreferencesKey("show_ping")
        val MAX_CONNECTIONS = intPreferencesKey("max_connections")
        val BANNED_IPS = stringSetPreferencesKey("banned_ips")
    }

    val autoRestartMax: Flow<Int> = context.dataStore.data.map { it[AUTO_RESTART_MAX] ?: 5 }
    val autoRestartNetworkChange: Flow<Boolean> = context.dataStore.data.map { it[AUTO_RESTART_NETWORK_CHANGE] ?: true }
    val showSpeed: Flow<Boolean> = context.dataStore.data.map { it[SHOW_SPEED] ?: true }
    val showPing: Flow<Boolean> = context.dataStore.data.map { it[SHOW_PING] ?: true }
    val maxConnections: Flow<Int> = context.dataStore.data.map { it[MAX_CONNECTIONS] ?: 10 }
    val bannedIps: Flow<Set<String>> = context.dataStore.data.map { it[BANNED_IPS] ?: emptySet() }

    suspend fun updateSettings(
        maxRestart: Int? = null,
        networkRestart: Boolean? = null,
        speed: Boolean? = null,
        ping: Boolean? = null,
        maxConn: Int? = null
    ) {
        context.dataStore.edit { preferences ->
            maxRestart?.let { preferences[AUTO_RESTART_MAX] = it }
            networkRestart?.let { preferences[AUTO_RESTART_NETWORK_CHANGE] = it }
            speed?.let { preferences[SHOW_SPEED] = it }
            ping?.let { preferences[SHOW_PING] = it }
            maxConn?.let { preferences[MAX_CONNECTIONS] = it }
        }
    }

    suspend fun banIp(ip: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BANNED_IPS] ?: emptySet()
            preferences[BANNED_IPS] = current + ip
        }
    }

    suspend fun unbanIp(ip: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[BANNED_IPS] ?: emptySet()
            preferences[BANNED_IPS] = current - ip
        }
    }
}
