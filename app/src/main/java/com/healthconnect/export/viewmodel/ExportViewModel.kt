package com.healthconnect.export.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.common.api.ApiException
import com.healthconnect.export.R
import com.healthconnect.export.data.*
import com.healthconnect.export.repository.HealthConnectRepository
import com.healthconnect.export.usecase.ExportDataUseCase
import com.healthconnect.export.usecase.ExportStep
import com.healthconnect.export.util.LocaleManager
import com.healthconnect.export.repository.LocalExportRepository
import com.healthconnect.export.repository.GoogleDriveRepository
import com.healthconnect.export.repository.WebhookRepository
import com.healthconnect.export.repository.WebhookResult
import com.healthconnect.export.worker.DailyExportWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


data class ExportUiState(
    val isLoading: Boolean = false,
    val exportProgress: String = "",
    val exportedFiles: List<File> = emptyList(),
    val driveStatus: DriveStatus = DriveStatus.NotConnected,
    val scheduleStatus: ScheduleStatus = ScheduleStatus.NotScheduled,
    val selectedTypes: Set<HealthDataType> = HealthDataType.entries.toSet(),
    val startDate: LocalDate = LocalDate.now().minusDays(7),
    val endDate: LocalDate = LocalDate.now().minusDays(1),
    val frequency: ExportFrequency = ExportFrequency.DAILY,
    val autoSyncDrive: Boolean = true,
    val webhookUrl: String = "",
    val webhookAuthToken: String = "",
    val autoSendWebhook: Boolean = false,
    val webhookUrlError: String? = null,
    val message: String? = null,
    val isDarkTheme: Boolean? = null,
    val locale: String? = null,
    val availableSources: List<String> = emptyList(),
    val selectedSourcePackage: String? = null,
    val sourcesLoading: Boolean = false,
    val exportSummary: ExportSummary? = null
)

sealed class DriveStatus {
    object NotConnected : DriveStatus()
    object Connected : DriveStatus()
    object Syncing : DriveStatus()
    data class Synced(val filesCount: Int) : DriveStatus()
    data class Error(val error: String) : DriveStatus()
}

sealed class ScheduleStatus {
    object NotScheduled : ScheduleStatus()
    data class Scheduled(val nextRun: String) : ScheduleStatus()
    object Running : ScheduleStatus()
}

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val healthRepo = HealthConnectRepository(getApplication())
    private val localRepo = LocalExportRepository(getApplication())
    private val driveRepo = GoogleDriveRepository(getApplication())
    private val webhookRepo = WebhookRepository()
    private val exportUseCase = ExportDataUseCase(healthRepo, localRepo)

    val googleSignInClient: GoogleSignInClient =
        GoogleSignIn.getClient(getApplication(), driveRepo.getSignInOptions())

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState = _uiState.asStateFlow()

    /** Набор разрешений для запроса Health Connect */
    var pendingPermissions: Set<String>? = null
        private set

    companion object {
        private const val PREFS_NAME = "healthconnect_export_prefs"
        private const val KEY_SELECTED_TYPES = "selected_types"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_START_DATE = "start_date"
        private const val KEY_END_DATE = "end_date"
        private const val KEY_WEBHOOK_URL = "webhook_url"
        private const val KEY_WEBHOOK_TOKEN = "webhook_auth_token"
        private const val KEY_AUTO_SEND_WEBHOOK = "auto_send_webhook"
        private const val KEY_AUTO_SYNC_DRIVE = "auto_sync_drive"
        private const val KEY_LOCALE = "app_locale"
        private const val KEY_SOURCE_PACKAGE = "selected_source_package"
    }

    // Helper to get localized strings from resources
    private fun str(id: Int): String = getApplication<Application>().getString(id)
    private fun str(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)

    init {
        loadSelectedTypes()
        loadThemePreference()
        loadDateRange()
        loadWebhookSettings()
        loadLocale()
        loadSourcePreference()
        refreshDriveStatus()
        refreshLocalFiles()
        scheduleExport()
        fetchAvailableSources()
    }

    private fun loadSelectedTypes() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_SELECTED_TYPES, null)
        if (saved != null && saved.isNotEmpty()) {
            val types = saved.mapNotNull { name ->
                try { HealthDataType.valueOf(name) } catch (_: IllegalArgumentException) { null }
            }.toSet()
            if (types.isNotEmpty()) {
                _uiState.update { it.copy(selectedTypes = types) }
            }
        }
    }

    private fun saveSelectedTypes(types: Set<HealthDataType>) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_SELECTED_TYPES, types.map { it.name }.toSet()).apply()
    }

    private fun loadThemePreference() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DARK_THEME)) {
            _uiState.update { it.copy(isDarkTheme = prefs.getBoolean(KEY_DARK_THEME, false)) }
        }
    }

    private fun saveThemePreference(darkTheme: Boolean?) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (darkTheme != null) {
            prefs.edit().putBoolean(KEY_DARK_THEME, darkTheme).apply()
        } else {
            prefs.edit().remove(KEY_DARK_THEME).apply()
        }
    }

    private fun loadDateRange() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val startStr = prefs.getString(KEY_START_DATE, null)
        val endStr = prefs.getString(KEY_END_DATE, null)
        if (startStr != null && endStr != null) {
            try {
                val start = LocalDate.parse(startStr)
                val end = LocalDate.parse(endStr)
                _uiState.update { it.copy(startDate = start, endDate = end) }
            } catch (_: Exception) { /* ignore invalid dates */ }
        }
    }

    private fun saveDateRange(start: LocalDate, end: LocalDate) {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_START_DATE, start.toString())
            .putString(KEY_END_DATE, end.toString())
            .apply()
    }

    private fun loadWebhookSettings() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_WEBHOOK_URL, "") ?: ""
        val token = prefs.getString(KEY_WEBHOOK_TOKEN, "") ?: ""
        val autoSend = prefs.getBoolean(KEY_AUTO_SEND_WEBHOOK, false)
        val autoSync = prefs.getBoolean(KEY_AUTO_SYNC_DRIVE, true)
        if (url.isNotBlank() || token.isNotBlank() || autoSend || !autoSync) {
            val error = if (url.isNotBlank() && !webhookRepo.isValidWebhookUrl(url)) {
                str(R.string.vm_invalid_url)
            } else null
            _uiState.update {
                it.copy(
                    webhookUrl = url,
                    webhookAuthToken = token,
                    autoSendWebhook = autoSend,
                    webhookUrlError = error,
                    autoSyncDrive = autoSync
                )
            }
        }
    }

    private fun saveWebhookUrl(url: String) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEBHOOK_URL, url).apply()
    }

    private fun saveWebhookToken(token: String) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_WEBHOOK_TOKEN, token).apply()
    }

    private fun saveAutoSendWebhook(enabled: Boolean) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SEND_WEBHOOK, enabled).apply()
    }

    private fun loadSourcePreference() {
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_SOURCE_PACKAGE, null)
        if (saved != null) {
            _uiState.update { it.copy(selectedSourcePackage = saved) }
        }
    }

    private fun saveSourcePreference(sourcePackage: String?) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SOURCE_PACKAGE, sourcePackage).apply()
    }

    fun setSourcePackage(sourcePackage: String?) {
        _uiState.update { it.copy(selectedSourcePackage = sourcePackage) }
        saveSourcePreference(sourcePackage)
    }

    fun fetchAvailableSources() {
        viewModelScope.launch {
            _uiState.update { it.copy(sourcesLoading = true) }
            try {
                val sources = healthRepo.getAvailableSources()
                _uiState.update {
                    it.copy(availableSources = sources.sorted(), sourcesLoading = false)
                }
            } catch (e: Exception) {
                Log.e("ExportViewModel", "Failed to fetch sources", e)
                _uiState.update { it.copy(sourcesLoading = false) }
            }
        }
    }

    private fun loadLocale() {
        val code = getApplication<Application>().let {
            LocaleManager.getSavedLocale(it)
        }
        _uiState.update { it.copy(locale = code) }
    }

    fun setLocale(code: String?) {
        _uiState.update { it.copy(locale = code) }
        getApplication<Application>().let {
            LocaleManager.saveLocale(it, code)
        }
    }

    private fun saveAutoSyncDrive(enabled: Boolean) {
        getApplication<Application>()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SYNC_DRIVE, enabled).apply()
    }

    fun setDarkTheme(darkTheme: Boolean?) {
        _uiState.update { it.copy(isDarkTheme = darkTheme) }
        saveThemePreference(darkTheme)
    }

    fun handleSignInResult(result: ActivityResult) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                _uiState.update {
                    it.copy(
                        driveStatus = DriveStatus.Connected,
                        message = str(R.string.vm_drive_connected, account.email)
                    )
                }
                refreshDriveStatus()
            }
        } catch (e: ApiException) {
            _uiState.update {
                it.copy(
                    driveStatus = DriveStatus.Error(str(R.string.vm_drive_signin_error, e.statusCode)),
                    message = str(R.string.vm_drive_signin_error, e.statusCode)
                )
            }
        }
    }

    fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            _uiState.update {
                it.copy(
                    driveStatus = DriveStatus.NotConnected,
                    message = str(R.string.vm_drive_signed_out)
                )
            }
        }
    }

    fun selectTypes(types: Set<HealthDataType>) {
        _uiState.update { it.copy(selectedTypes = types) }
        saveSelectedTypes(types)
    }

    fun setDateRange(start: LocalDate, end: LocalDate) {
        _uiState.update { it.copy(startDate = start, endDate = end) }
        saveDateRange(start, end)
    }

    fun setFrequency(freq: ExportFrequency) {
        _uiState.update { it.copy(frequency = freq) }
    }

    fun setAutoSyncDrive(enabled: Boolean) {
        _uiState.update { it.copy(autoSyncDrive = enabled) }
        saveAutoSyncDrive(enabled)
    }

    fun setWebhookUrl(url: String) {
        val error = if (url.isNotBlank() && !webhookRepo.isValidWebhookUrl(url)) {
            str(R.string.vm_invalid_url)
        } else null
        _uiState.update { it.copy(webhookUrl = url, webhookUrlError = error) }
        saveWebhookUrl(url)
    }

    fun setWebhookAuthToken(token: String) {
        _uiState.update { it.copy(webhookAuthToken = token) }
        saveWebhookToken(token)
    }

    fun setAutoSendWebhook(enabled: Boolean) {
        _uiState.update { it.copy(autoSendWebhook = enabled) }
        saveAutoSendWebhook(enabled)
    }

    fun scheduleExport() {
        val state = _uiState.value
        val config = ExportConfig(
            enabledTypes = state.selectedTypes,
            frequency = state.frequency,
            autoSyncDrive = state.autoSyncDrive,
            webhookUrl = state.webhookUrl,
            webhookAuthToken = state.webhookAuthToken,
            autoSendWebhook = state.autoSendWebhook
        )
        DailyExportWorker.schedule(getApplication(), config)
        _uiState.update {
            it.copy(
                scheduleStatus = ScheduleStatus.Scheduled(str(R.string.next_run)),
                message = str(R.string.schedule_set, config.frequency.displayName)
            )
        }
    }

    fun cancelSchedule() {
        DailyExportWorker.cancel(getApplication())
        _uiState.update {
            it.copy(
                scheduleStatus = ScheduleStatus.NotScheduled,
                message = str(R.string.schedule_cancelled)
            )
        }
    }

    fun exportNow() {
        Log.d("ExportViewModel", "exportNow() called")
        viewModelScope.launch {
            val state = _uiState.value

            exportUseCase.execute(
                context = getApplication(),
                config = ExportConfig(
                    enabledTypes = state.selectedTypes,
                    frequency = state.frequency,
                    autoSyncDrive = state.autoSyncDrive,
                    selectedSourcePackage = state.selectedSourcePackage
                ),
                startDate = state.startDate,
                endDate = state.endDate
            ).collect { step ->
                when (step) {
                    is ExportStep.CheckingPermissions -> {
                        _uiState.update {
                            it.copy(isLoading = true, exportProgress = str(R.string.vm_check_permissions))
                        }
                    }
                    is ExportStep.HealthNotAvailable -> {
                        _uiState.update {
                            it.copy(isLoading = false, message = str(R.string.vm_health_not_available))
                        }
                    }
                    is ExportStep.HealthNotInstalled -> {
                        _uiState.update {
                            it.copy(isLoading = false, message = str(R.string.vm_health_not_installed))
                        }
                    }
                    is ExportStep.PermissionsRequired -> {
                        pendingPermissions = step.permissions
                        _uiState.update {
                            it.copy(isLoading = false, message = str(R.string.vm_permissions_required))
                        }
                    }
                    is ExportStep.Progress -> {
                        _uiState.update { it.copy(exportProgress = step.message) }
                    }
                    is ExportStep.Complete -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                exportProgress = str(R.string.vm_saved_files, step.files.size),
                                exportedFiles = step.files,
                                exportSummary = step.summary,
                                message = str(R.string.vm_export_complete, step.files.size)
                            )
                        }
                        // Post-export: auto-sync to Drive
                        if (state.autoSyncDrive && driveRepo.isSignedIn()) {
                            syncToDrive(step.files)
                        }
                        // Post-export: send to webhook
                        if (state.autoSendWebhook && state.webhookUrl.isNotBlank()) {
                            sendToWebhook(state.webhookUrl, state.webhookAuthToken, step.records)
                        }
                    }
                    is ExportStep.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = step.message
                            )
                        }
                    }
                }
            }
        }
    }

    fun onPermissionsResult(grantedPermissions: Set<String>) {
        pendingPermissions = null
        viewModelScope.launch {
            val state = _uiState.value
            Log.d("ExportViewModel", "onPermissionsResult: launcher returned ${grantedPermissions.size}, checking via API...")
            val actualGranted = healthRepo.getGrantedPermissions()
            Log.d("ExportViewModel", "onPermissionsResult: API returned ${actualGranted.size} granted permissions")
            Log.d("ExportViewModel", "All granted permissions: $actualGranted")
            val required = healthRepo.getPermissionsForTypes(state.selectedTypes)
            if (actualGranted.containsAll(required)) {
                _uiState.update { it.copy(message = str(R.string.vm_permissions_granted)) }
                exportNow()
            } else {
                val missing = required - actualGranted
                val missingNames = missing.mapNotNull { perm ->
                    val type = HealthDataType.entries
                        .sortedByDescending { it.name.length }
                        .firstOrNull { perm.contains(it.name) }
                    if (type != null) {
                        val resName = "data_type_${type.name}"
                        val ctx = getApplication<Application>()
                        val resId = ctx.resources.getIdentifier(resName, "string", ctx.packageName)
                        if (resId != 0) str(resId) else type.displayName
                    } else null
                }
                _uiState.update {
                    it.copy(
                        message = str(R.string.vm_permissions_missing, missingNames.joinToString(", "))
                    )
                }
            }
        }
    }

    fun syncToDrive(files: List<File> = _uiState.value.exportedFiles) {
        if (!driveRepo.isSignedIn()) {
            _uiState.update {
                it.copy(
                    driveStatus = DriveStatus.NotConnected,
                    message = str(R.string.vm_drive_not_connected)
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(driveStatus = DriveStatus.Syncing) }
            try {
                val results = driveRepo.syncFiles(files)
                val syncedCount = results.count { it != null }
                _uiState.update {
                    it.copy(
                        driveStatus = DriveStatus.Synced(syncedCount),
                        message = str(R.string.vm_drive_synced, syncedCount)
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(driveStatus = DriveStatus.Error(e.message ?: "Unknown error"))
                }
            }
        }
    }

    fun refreshDriveStatus() {
        if (driveRepo.isSignedIn()) {
            _uiState.update { it.copy(driveStatus = DriveStatus.Connected) }
            viewModelScope.launch {
                val driveFiles = driveRepo.listDriveFiles()
                _uiState.update { it.copy(driveStatus = DriveStatus.Synced(driveFiles.size)) }
            }
        }
    }

    fun refreshLocalFiles() {
        val config = ExportConfig(
            enabledTypes = _uiState.value.selectedTypes,
            frequency = _uiState.value.frequency,
            autoSyncDrive = _uiState.value.autoSyncDrive
        )
        val files = localRepo.listExportedFiles(config).map { it.second }
        _uiState.update { it.copy(exportedFiles = files) }
    }

    private fun sendToWebhook(url: String, authToken: String, records: List<DailyHealthRecord>) {
        viewModelScope.launch {
            _uiState.update { it.copy(exportProgress = str(R.string.vm_sending_webhook)) }
            when (val result = webhookRepo.sendRecords(url, records, authToken)) {
                is WebhookResult.Success -> {
                    _uiState.update {
                        it.copy(
                            message = str(R.string.vm_webhook_success, result.statusCode)
                        )
                    }
                }
                is WebhookResult.Error -> {
                    _uiState.update {
                        it.copy(
                            message = str(R.string.vm_webhook_error, result.statusCode, result.message)
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(exportSummary = null) }
    }
}
