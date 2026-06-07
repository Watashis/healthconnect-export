package com.healthconnect.export.ui

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject
import com.healthconnect.export.R
import com.healthconnect.export.data.ExportFrequency
import com.healthconnect.export.data.HealthDataType
import com.healthconnect.export.data.sourceDisplayName
import com.healthconnect.export.viewmodel.DriveStatus
import com.healthconnect.export.viewmodel.ExportViewModel
import com.healthconnect.export.viewmodel.ScheduleStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onSignInClick: () -> Unit,
    onRequestHealthPermissions: (Set<String>) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(viewModel.pendingPermissions) {
        viewModel.pendingPermissions?.let { permissions ->
            onRequestHealthPermissions(permissions)
        }
    }

    // Состояние для JSON-просмотрщика
    var selectedJsonFile by remember { mutableStateOf<java.io.File?>(null) }

    // Диалог просмотра JSON
    selectedJsonFile?.let { file ->
        JsonViewerDialog(
            file = file,
            onDismiss = { selectedJsonFile = null }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Locale selector
                    var showLocaleMenu by remember { mutableStateOf(false) }
                    val currentLocale = uiState.locale
                    IconButton(onClick = { showLocaleMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = stringResource(R.string.locale_switch),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    DropdownMenu(
                        expanded = showLocaleMenu,
                        onDismissRequest = { showLocaleMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.locale_system)) },
                            onClick = {
                                showLocaleMenu = false
                                viewModel.setLocale(null)
                            },
                            leadingIcon = if (currentLocale == null) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.locale_en)) },
                            onClick = {
                                showLocaleMenu = false
                                viewModel.setLocale("en")
                            },
                            leadingIcon = if (currentLocale == "en") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.locale_ru)) },
                            onClick = {
                                showLocaleMenu = false
                                viewModel.setLocale("ru")
                            },
                            leadingIcon = if (currentLocale == "ru") {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }

                    // Theme toggle
                    val (themeIcon, themeDesc) = when {
                        uiState.isDarkTheme == null -> Icons.Default.Settings to R.string.theme_follow_system
                        uiState.isDarkTheme == true -> Icons.Default.DarkMode to R.string.theme_switch_dark
                        else -> Icons.Default.LightMode to R.string.theme_switch_light
                    }
                    IconButton(onClick = {
                        viewModel.setDarkTheme(
                            if (uiState.isDarkTheme == null) true
                            else if (uiState.isDarkTheme == true) false
                            else null
                        )
                    }) {
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = stringResource(themeDesc),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Drive Status Card
            item {
                DriveStatusCard(
                    status = uiState.driveStatus,
                    onSync = { viewModel.syncToDrive() },
                    onSignInClick = onSignInClick,
                    onSignOutClick = { viewModel.signOut() }
                )
            }

            // Webhook Card
            item {
                WebhookCard(
                    webhookUrl = uiState.webhookUrl,
                    webhookUrlError = uiState.webhookUrlError,
                    webhookAuthToken = uiState.webhookAuthToken,
                    autoSendWebhook = uiState.autoSendWebhook,
                    onUrlChange = { viewModel.setWebhookUrl(it) },
                    onTokenChange = { viewModel.setWebhookAuthToken(it) },
                    onToggle = { viewModel.setAutoSendWebhook(it) }
                )
            }

            // Schedule Settings
            item {
                ScheduleCard(
                    frequency = uiState.frequency,
                    scheduleStatus = uiState.scheduleStatus,
                    onFrequencyChange = { viewModel.setFrequency(it) },
                    onSchedule = { viewModel.scheduleExport() },
                    onCancel = { viewModel.cancelSchedule() }
                )
            }

            // Data Type Selection
            item {
                DataTypeCard(
                    selectedTypes = uiState.selectedTypes,
                    onTypeToggle = { type ->
                        val newTypes = if (uiState.selectedTypes.contains(type)) {
                            uiState.selectedTypes - type
                        } else {
                            uiState.selectedTypes + type
                        }
                        viewModel.selectTypes(newTypes)
                    },
                    onSelectAll = { viewModel.selectTypes(HealthDataType.entries.toSet()) },
                    onDeselectAll = { viewModel.selectTypes(emptySet()) }
                )
            }

            // Data Source Selection
            item {
                DataSourceCard(
                    availableSources = uiState.availableSources,
                    selectedSourcePackage = uiState.selectedSourcePackage,
                    sourcesLoading = uiState.sourcesLoading,
                    onSourceSelected = { viewModel.setSourcePackage(it) },
                    onRefresh = { viewModel.fetchAvailableSources() }
                )
            }

            // Date Range Selection
            item {
                DateRangeCard(
                    startDate = uiState.startDate,
                    endDate = uiState.endDate,
                    onDateRangeChange = { start, end -> viewModel.setDateRange(start, end) },
                    onStartDateChange = { viewModel.setDateRange(it, uiState.endDate) },
                    onEndDateChange = { viewModel.setDateRange(uiState.startDate, it) }
                )
            }

            // Auto-sync Drive checkbox
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = uiState.autoSyncDrive,
                        onCheckedChange = { viewModel.setAutoSyncDrive(it) }
                    )
                    Text(stringResource(R.string.auto_sync_drive))
                }
            }

            // Exported Files List
            if (uiState.exportedFiles.isNotEmpty()) {
                item(key = "exported_files_card") {
                    ExportedFilesCard(
                        files = uiState.exportedFiles,
                        onFileClick = { selectedJsonFile = it }
                    )
                }
            }

            // Dashboard Summary
            item(key = "dashboard_summary") {
                AnimatedVisibility(
                    visible = uiState.exportSummary != null,
                    enter = expandVertically(animationSpec = tween(400)) + fadeIn(tween(400)),
                    exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(tween(300))
                ) {
                    uiState.exportSummary?.let { summary ->
                        ExportSummaryCard(
                            summary = summary,
                            onDismiss = { viewModel.dismissSummary() }
                        )
                    }
                }
            }

            // Export Button with animated transitions
            item {
                Button(
                    onClick = { viewModel.exportNow() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = tween(durationMillis = 300)
                        )
                ) {
                    AnimatedContent(
                        targetState = uiState.isLoading,
                        transitionSpec = {
                            if (targetState) {
                                ContentTransform(
                                    targetContentEnter = slideInVertically { it } + fadeIn(animationSpec = tween(300)),
                                    initialContentExit = slideOutVertically { -it } + fadeOut(animationSpec = tween(200))
                                )
                            } else {
                                ContentTransform(
                                    targetContentEnter = slideInVertically { -it } + fadeIn(animationSpec = tween(300)),
                                    initialContentExit = slideOutVertically { it } + fadeOut(animationSpec = tween(200))
                                )
                            }
                        },
                        label = "exportButtonState"
                    ) { isLoading ->
                        if (isLoading) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedContent(
                                    targetState = uiState.progressPhase,
                                    transitionSpec = {
                                        ContentTransform(
                                            targetContentEnter = slideInVertically { it } + fadeIn(animationSpec = tween(250)),
                                            initialContentExit = slideOutVertically { -it } + fadeOut(animationSpec = tween(200))
                                        )
                                    },
                                    label = "exportProgressPhase"
                                ) { phase ->
                                    when (phase) {
                                        "read" -> {
                                            val progress = if (uiState.progressTotal > 0)
                                                uiState.progressCurrent.toFloat() / uiState.progressTotal.toFloat()
                                            else 0f
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    stringResource(R.string.export_progress_reading, uiState.progressDate),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    "${uiState.progressCurrent}/${uiState.progressTotal}",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        "save" -> {
                                            val progress = if (uiState.progressTotal > 0)
                                                uiState.progressCurrent.toFloat() / uiState.progressTotal.toFloat()
                                            else 0f
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    stringResource(R.string.export_progress_saving, uiState.progressDate),
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { progress },
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    "${uiState.progressCurrent}/${uiState.progressTotal}",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                        else -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(uiState.exportProgress)
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        stringResource(R.string.cancel_export),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        )
                                    )
                                }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Save, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.export_data_types, uiState.selectedTypes.size))
                            }
                        }
                    }
                }
            }


            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

// ===== Localized display name helpers =====

@Composable
private fun exportFrequencyDisplayName(freq: ExportFrequency): String = when (freq) {
    ExportFrequency.MANUAL -> stringResource(R.string.freq_manual)
    ExportFrequency.DAILY -> stringResource(R.string.freq_daily)
    ExportFrequency.WEEKLY -> stringResource(R.string.freq_weekly)
}

@Composable
private fun healthDataTypeDisplayName(type: HealthDataType): String = when (type) {
    HealthDataType.STEPS -> stringResource(R.string.data_type_STEPS)
    HealthDataType.HEART_RATE -> stringResource(R.string.data_type_HEART_RATE)
    HealthDataType.SLEEP -> stringResource(R.string.data_type_SLEEP)
    HealthDataType.CALORIES -> stringResource(R.string.data_type_CALORIES)
    HealthDataType.DISTANCE -> stringResource(R.string.data_type_DISTANCE)
    HealthDataType.FLOORS_CLIMBED -> stringResource(R.string.data_type_FLOORS_CLIMBED)
    HealthDataType.ACTIVE_CALORIES -> stringResource(R.string.data_type_ACTIVE_CALORIES)
    HealthDataType.WEIGHT -> stringResource(R.string.data_type_WEIGHT)
    HealthDataType.BODY_FAT -> stringResource(R.string.data_type_BODY_FAT)
    HealthDataType.BLOOD_PRESSURE -> stringResource(R.string.data_type_BLOOD_PRESSURE)
    HealthDataType.BLOOD_GLUCOSE -> stringResource(R.string.data_type_BLOOD_GLUCOSE)
    HealthDataType.OXYGEN_SATURATION -> stringResource(R.string.data_type_OXYGEN_SATURATION)
    HealthDataType.BODY_TEMPERATURE -> stringResource(R.string.data_type_BODY_TEMPERATURE)
    HealthDataType.RESPIRATORY_RATE -> stringResource(R.string.data_type_RESPIRATORY_RATE)
    HealthDataType.HYDRATION -> stringResource(R.string.data_type_HYDRATION)
    HealthDataType.RESTING_HEART_RATE -> stringResource(R.string.data_type_RESTING_HEART_RATE)
    HealthDataType.EXERCISE -> stringResource(R.string.data_type_EXERCISE)
    HealthDataType.NUTRITION -> stringResource(R.string.data_type_NUTRITION)
    HealthDataType.SPEED -> stringResource(R.string.data_type_SPEED)
    HealthDataType.MENSTRUATION -> stringResource(R.string.data_type_MENSTRUATION)
}

// ===== Drive Status Card =====

@Composable
fun DriveStatusCard(status: DriveStatus, onSync: () -> Unit, onSignInClick: () -> Unit, onSignOutClick: () -> Unit) {
    val (icon, title, color) = when (status) {
        is DriveStatus.NotConnected ->
            Triple(Icons.Default.CloudOff, stringResource(R.string.drive_not_connected), MaterialTheme.colorScheme.error)
        is DriveStatus.Connected ->
            Triple(Icons.Default.Cloud, stringResource(R.string.drive_connected), MaterialTheme.colorScheme.primary)
        is DriveStatus.Syncing ->
            Triple(Icons.Default.Refresh, stringResource(R.string.drive_syncing), MaterialTheme.colorScheme.tertiary)
        is DriveStatus.Synced ->
            Triple(Icons.Default.Cloud, stringResource(R.string.drive_synced, status.filesCount), MaterialTheme.colorScheme.primary)
        is DriveStatus.Error ->
            Triple(Icons.Default.CloudOff, stringResource(R.string.drive_error, status.error), MaterialTheme.colorScheme.error)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = color
            )
            when (status) {
                is DriveStatus.NotConnected, is DriveStatus.Error -> {
                    TextButton(onClick = onSignInClick) {
                        Text(stringResource(R.string.sign_in))
                    }
                }
                is DriveStatus.Connected, is DriveStatus.Synced -> {
                    TextButton(onClick = onSync) {
                        Text(stringResource(R.string.sync_now))
                    }
                    TextButton(onClick = onSignOutClick) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
                is DriveStatus.Syncing -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

// ===== Schedule Card =====

@Composable
fun ScheduleCard(
    frequency: ExportFrequency,
    scheduleStatus: ScheduleStatus,
    onFrequencyChange: (ExportFrequency) -> Unit,
    onSchedule: () -> Unit,
    onCancel: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.scheduled_export),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            ExportFrequency.entries.forEach { freq ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = frequency == freq,
                        onClick = { onFrequencyChange(freq) }
                    )
                    Text(exportFrequencyDisplayName(freq))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (scheduleStatus) {
                is ScheduleStatus.NotScheduled -> {
                    if (frequency != ExportFrequency.MANUAL) {
                        Button(onClick = onSchedule, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.enable_schedule))
                        }
                    }
                }
                is ScheduleStatus.Scheduled -> {
                    Text(scheduleStatus.nextRun, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.disable))
                    }
                }
                is ScheduleStatus.Running -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ===== Data Type Selection Card =====

private enum class DataTypeCategory(val icon: ImageVector) {
    ACTIVITY(Icons.Default.Bolt),
    VITALS(Icons.Default.Favorite),
    BODY(Icons.Default.Person)
}

@Composable
private fun dataTypeCategoryDisplayName(category: DataTypeCategory): String = when (category) {
    DataTypeCategory.ACTIVITY -> stringResource(R.string.category_activity)
    DataTypeCategory.VITALS -> stringResource(R.string.category_vitals)
    DataTypeCategory.BODY -> stringResource(R.string.category_body)
}

private fun categoryForType(type: HealthDataType): DataTypeCategory = when (type) {
    HealthDataType.STEPS, HealthDataType.DISTANCE, HealthDataType.FLOORS_CLIMBED,
    HealthDataType.CALORIES, HealthDataType.ACTIVE_CALORIES,
    HealthDataType.EXERCISE, HealthDataType.SPEED -> DataTypeCategory.ACTIVITY

    HealthDataType.HEART_RATE, HealthDataType.RESTING_HEART_RATE,
    HealthDataType.BLOOD_PRESSURE, HealthDataType.BLOOD_GLUCOSE,
    HealthDataType.OXYGEN_SATURATION, HealthDataType.BODY_TEMPERATURE,
    HealthDataType.RESPIRATORY_RATE -> DataTypeCategory.VITALS

    HealthDataType.WEIGHT, HealthDataType.BODY_FAT, HealthDataType.HYDRATION,
    HealthDataType.SLEEP, HealthDataType.NUTRITION, HealthDataType.MENSTRUATION -> DataTypeCategory.BODY
}

private val dataTypesByCategory: Map<DataTypeCategory, List<HealthDataType>> by lazy {
    HealthDataType.entries.groupBy { categoryForType(it) }
}

@Composable
fun DataTypeCard(
    selectedTypes: Set<HealthDataType>,
    onTypeToggle: (HealthDataType) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    val allSelected = selectedTypes.size == HealthDataType.entries.size
    val noneSelected = selectedTypes.isEmpty()

    // Состояние раскрытия категорий — все раскрыты по умолчанию
    val expandedStates = remember {
        mutableStateMapOf<DataTypeCategory, Boolean>().apply {
            DataTypeCategory.entries.forEach { put(it, true) }
        }
    }

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.data_types),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (noneSelected) MaterialTheme.colorScheme.surfaceVariant
                             else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${selectedTypes.size} / ${HealthDataType.entries.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (noneSelected) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Select / Deselect All
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectAll,
                    enabled = !allSelected,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.select_all), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onDeselectAll,
                    enabled = !noneSelected,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.deselect_all), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand / Collapse All buttons
            val allExpanded = DataTypeCategory.entries.all { expandedStates[it] == true }
            val allCollapsed = DataTypeCategory.entries.all { expandedStates[it] == false }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        DataTypeCategory.entries.forEach { expandedStates[it] = true }
                    },
                    enabled = !allExpanded,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.expand_all), style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = {
                        DataTypeCategory.entries.forEach { expandedStates[it] = false }
                    },
                    enabled = !allCollapsed,
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(stringResource(R.string.collapse_all), style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Collapsible categories
            DataTypeCategory.entries.forEachIndexed { index, category ->
                val types = dataTypesByCategory[category] ?: return@forEachIndexed
                val selectedInCategory = types.count { selectedTypes.contains(it) }
                val expanded = expandedStates[category] ?: true

                // Clickable category header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedStates[category] = !expanded
                        }
                        .padding(vertical = 4.dp)
                ) {
                    // Icon badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = dataTypeCategoryDisplayName(category),
                            modifier = Modifier
                                .padding(6.dp)
                                .size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    // Title
                    Text(
                        dataTypeCategoryDisplayName(category),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )

                    // Selected counter
                    Text(
                        "$selectedInCategory / ${types.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))

                    // Animated chevron
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "chevronRotation"
                    )
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) stringResource(R.string.collapse_section)
                                             else stringResource(R.string.expand_section),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Animated content
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(
                        animationSpec = tween(durationMillis = 250)
                    ),
                    exit = shrinkVertically(
                        animationSpec = tween(durationMillis = 200)
                    )
                ) {
                    val mid = (types.size + 1) / 2
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            types.take(mid).forEach { type ->
                                DataTypeRow(type, selectedTypes, onTypeToggle)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            types.drop(mid).forEach { type ->
                                DataTypeRow(type, selectedTypes, onTypeToggle)
                            }
                        }
                    }
                }

                // Spacer between categories
                if (index < DataTypeCategory.entries.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DataTypeRow(
    type: HealthDataType,
    selectedTypes: Set<HealthDataType>,
    onTypeToggle: (HealthDataType) -> Unit
) {
    val displayName = healthDataTypeDisplayName(type)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        Checkbox(
            checked = selectedTypes.contains(type),
            onCheckedChange = { onTypeToggle(type) },
            modifier = Modifier.size(20.dp)
        )
        Icon(
            imageVector = iconForType(type),
            contentDescription = displayName,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            displayName,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun iconForType(type: HealthDataType): ImageVector = when (type) {
    HealthDataType.STEPS -> Icons.Default.DirectionsWalk
    HealthDataType.HEART_RATE -> Icons.Default.Favorite
    HealthDataType.SLEEP -> Icons.Default.Nightlight
    HealthDataType.CALORIES -> Icons.Default.LocalFireDepartment
    HealthDataType.DISTANCE -> Icons.Default.Straighten
    HealthDataType.FLOORS_CLIMBED -> Icons.Default.Stairs
    HealthDataType.ACTIVE_CALORIES -> Icons.Default.Bolt
    HealthDataType.WEIGHT -> Icons.Default.MonitorWeight
    HealthDataType.BODY_FAT -> Icons.Default.Scale
    HealthDataType.BLOOD_PRESSURE -> Icons.Default.FavoriteBorder
    HealthDataType.BLOOD_GLUCOSE -> Icons.Default.Bloodtype
    HealthDataType.OXYGEN_SATURATION -> Icons.Default.Air
    HealthDataType.BODY_TEMPERATURE -> Icons.Default.DeviceThermostat
    HealthDataType.RESPIRATORY_RATE -> Icons.Default.Air
    HealthDataType.HYDRATION -> Icons.Default.WaterDrop
    HealthDataType.RESTING_HEART_RATE -> Icons.Default.FavoriteBorder
    HealthDataType.EXERCISE -> Icons.Default.FitnessCenter
    HealthDataType.NUTRITION -> Icons.Default.Restaurant
    HealthDataType.SPEED -> Icons.Default.Speed
    HealthDataType.MENSTRUATION -> Icons.Default.CalendarMonth
}

// ===== Date Range Card =====

@Composable
fun DateRangeCard(
    startDate: LocalDate,
    endDate: LocalDate,
    onDateRangeChange: (LocalDate, LocalDate) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit
) {
    val context = LocalContext.current

    val preset7dStart = LocalDate.now().minusDays(6)
    val preset7dEnd = LocalDate.now()
    val preset30dStart = LocalDate.now().minusDays(29)
    val preset30dEnd = LocalDate.now()

    val is7dPreset = startDate == preset7dStart && endDate == preset7dEnd
    val is30dPreset = startDate == preset30dStart && endDate == preset30dEnd

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.export_period), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 7 days preset
                if (is7dPreset) {
                    Button(
                        onClick = { onDateRangeChange(preset7dStart, preset7dEnd) }
                    ) {
                        Text(stringResource(R.string.days_7))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDateRangeChange(preset7dStart, preset7dEnd) }
                    ) {
                        Text(stringResource(R.string.days_7))
                    }
                }
                // 30 days preset
                if (is30dPreset) {
                    Button(
                        onClick = { onDateRangeChange(preset30dStart, preset30dEnd) }
                    ) {
                        Text(stringResource(R.string.days_30))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onDateRangeChange(preset30dStart, preset30dEnd) }
                    ) {
                        Text(stringResource(R.string.days_30))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DatePickerButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.from_label),
                    date = startDate,
                    onDateSelected = onStartDateChange
                )
                DatePickerButton(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.to_label),
                    date = endDate,
                    onDateSelected = onEndDateChange
                )
            }
        }
    }
}

@Composable
fun DatePickerButton(modifier: Modifier = Modifier, label: String, date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current

    // Check if date is at a preset value (defaults)
    val isPreset = date == LocalDate.now().minusDays(6) ||
        date == LocalDate.now() ||
        date == LocalDate.now().minusDays(29)

    val containerColor = if (isPreset) MaterialTheme.colorScheme.surfaceVariant
                         else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (isPreset) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onPrimaryContainer
    val labelColor = if (isPreset) MaterialTheme.colorScheme.onSurfaceVariant
                     else MaterialTheme.colorScheme.onPrimaryContainer

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = modifier
            .height(56.dp)
            .clickable {
                val calendar = Calendar.getInstance()
                calendar.set(date.year, date.monthValue - 1, date.dayOfMonth)
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onDateSelected(LocalDate.of(year, month + 1, day))
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = labelColor
                )
                Text(
                    text = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
    }
}

// ===== JSON Viewer Dialog =====

@Composable
private fun JsonViewerDialog(file: java.io.File, onDismiss: () -> Unit) {
    val context = LocalContext.current

    // Читаем и форматируем JSON в background корутине
    var prettyJson by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val openErrorMsg = stringResource(R.string.json_viewer_open_error)

    LaunchedEffect(file) {
        try {
            val content = file.readText()
            prettyJson = JSONObject(content).toString(2)
        } catch (e: Exception) {
            error = openErrorMsg
        }
    }

    // Всегда используем тёмный фон для JSON-просмотрщика (как в IDE/редакторах кода)
    val viewerBackground = Color(0xFF1E1E1E)
    val viewerTextColor = Color(0xFFD4D4D4)
    val viewerHeaderBg = Color(0xFF2D2D2D)
    val viewerDividerColor = Color(0xFF3C3C3C)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            color = viewerBackground
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(viewerHeaderBg)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFF569CD6),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.json_viewer_title, file.name),
                        style = MaterialTheme.typography.titleMedium,
                        color = viewerTextColor,
                        modifier = Modifier.weight(1f)
                    )
                    // Copy to clipboard button
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(file.name, prettyJson ?: "")
                        clipboard.setPrimaryClip(clip)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.json_viewer_copy),
                            tint = Color(0xFF6A9955)
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.json_viewer_close),
                            tint = viewerTextColor
                        )
                    }
                }

                HorizontalDivider(color = viewerDividerColor)

                // JSON content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    when {
                        error != null -> {
                            Text(
                                text = error!!,
                                color = Color(0xFFD16969),
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        prettyJson != null -> {
                            val scrollState = rememberScrollState()
                            val formattedJson = prettyJson!!

                            Text(
                                text = highlightJsonSyntax(formattedJson),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = viewerTextColor
                                ),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(12.dp)
                            )
                        }
                        else -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color(0xFF569CD6)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Простая подсветка синтаксиса JSON с помощью AnnotatedString.
 */
internal fun highlightJsonSyntax(json: String): androidx.compose.ui.text.AnnotatedString {
    val keyColor = Color(0xFF569CD6)       // синий — ключи
    val stringColor = Color(0xFF6A9955)     // зелёный — строки
    val numberColor = Color(0xFFB5CEA8)     // салатовый — числа
    val boolColor = Color(0xFF569CD6)       // синий — true/false
    val nullColor = Color(0xFFD16969)       // красный — null
    val defaultColor = Color(0xFFD4D4D4)    // серый — скобки, запятые

    return buildAnnotatedString {
        var i = 0
        while (i < json.length) {
            when {
                // Строка (значение или ключ)
                json[i] == '"' -> {
                    val start = i
                    i++
                    while (i < json.length) {
                        if (json[i] == '"') {
                            // Считаем последовательные бэкслеши перед кавычкой
                            var slashCount = 0
                            var j = i - 1
                            while (j >= 0 && json[j] == '\\') {
                                slashCount++
                                j--
                            }
                            // Кавычка экранирована только при НЕчётном числе бэкслешей
                            if (slashCount % 2 == 0) break
                        }
                        i++
                    }
                    if (i < json.length) i++
                    val token = json.substring(start, i)

                    // Определяем, ключ это или значение
                    // Ищем ':' после строки, игнорируя пробелы
                    var j = i
                    while (j < json.length && json[j].isWhitespace()) j++
                    val isKey = j < json.length && json[j] == ':'

                    withStyle(SpanStyle(color = if (isKey) keyColor else stringColor)) {
                        append(token)
                    }
                }
                // Числа (включая отрицательные и с точкой)
                json[i] == '-' || json[i].isDigit() -> {
                    val start = i
                    if (json[i] == '-') i++
                    while (i < json.length && (json[i].isDigit() || json[i] == '.' || json[i] == 'e' || json[i] == 'E' || json[i] == '+' || json[i] == '-')) i++
                    // Уточняем, что это не -1 в индексе и не другое
                    withStyle(SpanStyle(color = numberColor)) {
                        append(json.substring(start, i))
                    }
                }
                // true / false
                json.startsWith("true", i) -> {
                    withStyle(SpanStyle(color = boolColor, fontWeight = FontWeight.SemiBold)) {
                        append("true")
                    }
                    i += 4
                }
                json.startsWith("false", i) -> {
                    withStyle(SpanStyle(color = boolColor, fontWeight = FontWeight.SemiBold)) {
                        append("false")
                    }
                    i += 5
                }
                // null
                json.startsWith("null", i) -> {
                    withStyle(SpanStyle(color = nullColor, fontWeight = FontWeight.SemiBold)) {
                        append("null")
                    }
                    i += 4
                }
                // Всё остальное (скобки, запятые, двоеточия, пробелы, переводы строк)
                else -> {
                    withStyle(SpanStyle(color = defaultColor)) {
                        append(json[i].toString())
                    }
                    i++
                }
            }
        }
    }
}

// ===== Exported Files Card =====

@Composable
fun ExportedFilesCard(files: List<java.io.File>, onFileClick: (java.io.File) -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.exported_files_title, files.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            files.take(5).forEach { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFileClick(file) }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = formatFileSize(file.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (files.size > 5) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.exported_files_more, files.size - 5),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> stringResource(R.string.file_size_bytes, bytes)
        bytes < 1024 * 1024 -> stringResource(R.string.file_size_kb, bytes / 1024)
        else -> stringResource(R.string.file_size_mb, bytes / (1024.0 * 1024.0))
    }
}

// ===== Export Summary (Dashboard) Card =====

private fun formatNumber(value: Long): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format("%,d", value)
        else -> value.toString()
    }
}

private fun formatMinutesToHours(minutes: Long): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

@Composable
fun ExportSummaryCard(
    summary: com.healthconnect.export.data.ExportSummary,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.dashboard_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.dashboard_dismiss),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Period
            Text(
                stringResource(R.string.dashboard_period, summary.startDate, summary.endDate, summary.daysCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics grid — 3 columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Steps
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.DirectionsWalk,
                    value = formatNumber(summary.totalSteps),
                    label = stringResource(R.string.dashboard_label_steps),
                    available = summary.totalSteps > 0
                )
                // Heart Rate
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Favorite,
                    value = if (summary.avgHeartRate > 0) "${summary.avgHeartRate.toInt()} bpm" else stringResource(R.string.dashboard_no_data),
                    label = stringResource(R.string.dashboard_label_hr),
                    available = summary.avgHeartRate > 0
                )
                // Calories
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LocalFireDepartment,
                    value = if (summary.totalCalories > 0) formatNumber(summary.totalCalories.toLong()) else stringResource(R.string.dashboard_no_data),
                    label = stringResource(R.string.dashboard_label_calories),
                    available = summary.totalCalories > 0
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Second row — 3 columns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Distance
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Straighten,
                    value = if (summary.totalDistanceMeters > 0) {
                        val km = summary.totalDistanceMeters / 1000.0
                        if (km >= 1) String.format("%.1f km", km) else "${summary.totalDistanceMeters.toInt()} m"
                    } else stringResource(R.string.dashboard_no_data),
                    label = stringResource(R.string.dashboard_label_distance),
                    available = summary.totalDistanceMeters > 0
                )
                // Sleep
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Nightlight,
                    value = if (summary.avgSleepMinutes > 0) formatMinutesToHours(summary.avgSleepMinutes) else stringResource(R.string.dashboard_no_data),
                    label = stringResource(R.string.dashboard_label_sleep),
                    available = summary.avgSleepMinutes > 0
                )
                // Active Calories
                StatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Bolt,
                    value = if (summary.totalActiveCalories > 0) formatNumber(summary.totalActiveCalories.toLong()) else stringResource(R.string.dashboard_no_data),
                    label = stringResource(R.string.dashboard_label_active_cal),
                    available = summary.totalActiveCalories > 0
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dismiss button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.dashboard_dismiss))
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    available: Boolean
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (available) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = if (available) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = if (available) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ===== Webhook Card =====

@Composable
fun WebhookCard(
    webhookUrl: String,
    webhookUrlError: String?,
    webhookAuthToken: String,
    autoSendWebhook: Boolean,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val urlHasError = webhookUrl.isNotBlank() && webhookUrlError != null

    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Http, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.webhook),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = webhookUrl,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.webhook_url_label)) },
                placeholder = { Text(stringResource(R.string.webhook_url_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = urlHasError,
                supportingText = if (urlHasError) {
                    { Text(webhookUrlError ?: "", color = MaterialTheme.colorScheme.error) }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = webhookAuthToken,
                onValueChange = onTokenChange,
                label = { Text(stringResource(R.string.bearer_token_label)) },
                placeholder = { Text(stringResource(R.string.bearer_token_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = autoSendWebhook,
                    onCheckedChange = onToggle,
                    enabled = webhookUrl.isNotBlank()
                )
                Text(
                    if (webhookUrl.isBlank()) stringResource(R.string.enter_url_first)
                    else stringResource(R.string.send_json)
                )
            }
        }
    }
}

// ===== Data Source Selection Card =====

@Composable
fun DataSourceCard(
    availableSources: List<String>,
    selectedSourcePackage: String?,
    sourcesLoading: Boolean,
    onSourceSelected: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.data_source),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (sourcesLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.scanning_sources),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (availableSources.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.no_sources_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRefresh) {
                        Text(stringResource(R.string.refresh))
                    }
                }
            } else {
                // Auto option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clickable { onSourceSelected(null) }
                ) {
                    RadioButton(
                        selected = selectedSourcePackage == null,
                        onClick = { onSourceSelected(null) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.source_auto),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.source_auto_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Source list
                availableSources.forEach { packageName ->
                    val isSelected = selectedSourcePackage == packageName
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onSourceSelected(packageName) }
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSourceSelected(packageName) }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sourceDisplayName(packageName),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = packageName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Refresh button
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.refresh_sources))
                }
            }
        }
    }
}
