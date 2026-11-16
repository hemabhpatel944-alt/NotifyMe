package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notifyme_prefs")

class LocalPreferencesStore(private val context: Context) {

    private object Keys {
        val DEMO_MODE = booleanPreferencesKey("demo_mode_enabled")
        val MCP_PORT = intPreferencesKey("mcp_server_port")
        val LAN_BINDING = booleanPreferencesKey("mcp_lan_binding")
        val DARK_THEME_FALLBACK = booleanPreferencesKey("dark_theme_fallback")
    }

    val demoModeFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.DEMO_MODE] ?: false }
    val mcpPortFlow: Flow<Int> = context.dataStore.data.map { it[Keys.MCP_PORT] ?: 8765 }
    val lanBindingFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.LAN_BINDING] ?: false }
    val darkThemeFallbackFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_THEME_FALLBACK] ?: true }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEMO_MODE] = enabled }
    }

    suspend fun setMcpPort(port: Int) {
        val safePort = port.coerceIn(1024, 65535)
        context.dataStore.edit { it[Keys.MCP_PORT] = safePort }
    }

    suspend fun setLanBinding(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LAN_BINDING] = enabled }
    }

    suspend fun setDarkThemeFallback(dark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME_FALLBACK] = dark }
    }
}
