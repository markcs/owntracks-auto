package com.owntracks.auto.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Preferred navigation app for launching directions. */
enum class NavApp { ASK, GOOGLE_MAPS, WAZE }

/** Automatic refresh of device and track data while the app is on screen. */
data class AutoRefresh(val enabled: Boolean, val intervalSeconds: Int)

/**
 * Optional HTTP Basic authentication credentials for the server. When both
 * [username] and [password] are blank, no `Authorization` header is sent.
 */
data class BasicAuth(val username: String, val password: String) {
    /** Whether a username has been configured (the trigger for sending auth). */
    val isEnabled: Boolean get() = username.isNotBlank()
}

/**
 * Persists the configurable base URL, navigation-app preference,
 * auto-refresh options and HTTP Basic credentials using Jetpack DataStore.
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val NAV_APP = stringPreferencesKey("nav_app")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        val REFRESH_SECONDS = intPreferencesKey("refresh_seconds")
        val AUTH_USERNAME = stringPreferencesKey("auth_username")
        val AUTH_PASSWORD = stringPreferencesKey("auth_password")
    }

    val baseUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.BASE_URL] ?: DEFAULT_BASE_URL
    }

    val basicAuthFlow: Flow<BasicAuth> = context.dataStore.data.map { prefs ->
        BasicAuth(
            username = prefs[Keys.AUTH_USERNAME].orEmpty(),
            password = prefs[Keys.AUTH_PASSWORD].orEmpty(),
        )
    }.distinctUntilChanged()

    val navAppFlow: Flow<NavApp> = context.dataStore.data.map { prefs ->
        runCatching { NavApp.valueOf(prefs[Keys.NAV_APP] ?: NavApp.ASK.name) }
            .getOrDefault(NavApp.ASK)
    }

    val autoRefreshFlow: Flow<AutoRefresh> = context.dataStore.data.map { prefs ->
        val seconds = prefs[Keys.REFRESH_SECONDS]?.takeIf { it in REFRESH_INTERVALS_SECONDS }
            ?: DEFAULT_REFRESH_SECONDS
        AutoRefresh(enabled = prefs[Keys.AUTO_REFRESH] ?: false, intervalSeconds = seconds)
    }.distinctUntilChanged()

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = url }
    }

    suspend fun setBasicAuth(username: String, password: String) {
        context.dataStore.edit {
            it[Keys.AUTH_USERNAME] = username.trim()
            it[Keys.AUTH_PASSWORD] = password
        }
    }

    suspend fun setNavApp(app: NavApp) {
        context.dataStore.edit { it[Keys.NAV_APP] = app.name }
    }

    suspend fun setAutoRefreshEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_REFRESH] = enabled }
    }

    suspend fun setRefreshIntervalSeconds(seconds: Int) {
        require(seconds in REFRESH_INTERVALS_SECONDS) { "Unsupported interval: $seconds" }
        context.dataStore.edit { it[Keys.REFRESH_SECONDS] = seconds }
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://192.168.1.254:8084"

        /** Intervals offered in Settings. OwnTracks clients typically report every 30 s or more. */
        val REFRESH_INTERVALS_SECONDS = listOf(30, 60, 120, 300, 600, 900)
        const val DEFAULT_REFRESH_SECONDS = 60
    }
}
