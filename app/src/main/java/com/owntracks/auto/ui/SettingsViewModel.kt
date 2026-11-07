package com.owntracks.auto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.owntracks.auto.data.ApiClientFactory
import com.owntracks.auto.settings.AutoRefresh
import com.owntracks.auto.settings.BasicAuth
import com.owntracks.auto.settings.NavApp
import com.owntracks.auto.settings.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TestResult {
    data object Idle : TestResult
    data object Testing : TestResult
    data class Success(val message: String) : TestResult
    data class Failure(val message: String) : TestResult
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsStore(app)

    val baseUrl: StateFlow<String> = settings.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_BASE_URL)

    val basicAuth: StateFlow<BasicAuth> = settings.basicAuthFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, BasicAuth("", ""))

    val navApp: StateFlow<NavApp> = settings.navAppFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NavApp.ASK)

    val autoRefresh: StateFlow<AutoRefresh> = settings.autoRefreshFlow
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AutoRefresh(enabled = false, intervalSeconds = SettingsStore.DEFAULT_REFRESH_SECONDS),
        )

    fun setAutoRefreshEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAutoRefreshEnabled(enabled) }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch { settings.setRefreshIntervalSeconds(seconds) }
    }

    private val _testResult = MutableStateFlow<TestResult>(TestResult.Idle)
    val testResult: StateFlow<TestResult> = _testResult.asStateFlow()

    fun saveBaseUrl(url: String) {
        viewModelScope.launch { settings.setBaseUrl(ApiClientFactory.normalizeBaseUrl(url)) }
    }

    fun saveServer(url: String, username: String, password: String) {
        viewModelScope.launch {
            settings.setBaseUrl(ApiClientFactory.normalizeBaseUrl(url))
            settings.setBasicAuth(username, password)
        }
    }

    fun saveNavApp(app: NavApp) {
        viewModelScope.launch { settings.setNavApp(app) }
    }

    /** Hit `/latest-position` on the given (or saved) URL to verify reachability. */
    fun testConnection(url: String, username: String, password: String) {
        viewModelScope.launch {
            _testResult.value = TestResult.Testing
            runCatching {
                val service = ApiClientFactory.create(
                    url.ifBlank { settings.baseUrlFlow.first() },
                    BasicAuth(username.trim(), password),
                )
                service.latestPosition()
            }.onSuccess { pos ->
                _testResult.value = TestResult.Success(
                    if (pos == null) "Connected. No positions recorded yet."
                    else "Connected. Latest device #${pos.deviceId}.",
                )
            }.onFailure { e ->
                _testResult.value = TestResult.Failure(e.message ?: "Connection failed")
            }
        }
    }
}
