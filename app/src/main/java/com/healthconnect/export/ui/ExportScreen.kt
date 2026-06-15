package com.healthconnect.export.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.healthconnect.export.R
import com.healthconnect.export.data.HealthDataType
import com.healthconnect.export.ui.components.DataSourceCard
import com.healthconnect.export.ui.components.DataTypeCard
import com.healthconnect.export.ui.components.DateRangeCard
import com.healthconnect.export.ui.components.DriveStatusCard
import com.healthconnect.export.ui.components.ExportedFilesCard
import com.healthconnect.export.ui.components.ExportSummaryCard
import com.healthconnect.export.ui.components.JsonViewerDialog
import com.healthconnect.export.ui.components.ScheduleCard
import com.healthconnect.export.ui.components.WebhookCard
import com.healthconnect.export.viewmodel.ExportViewModel
import java.io.File

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

    // Store file path (not File) to avoid stale references on recreate
    var selectedJsonFilePath by remember { mutableStateOf<String?>(null) }

    selectedJsonFilePath?.let { path ->
        JsonViewerDialog(
            file = File(path),
            onDismiss = { selectedJsonFilePath = null }
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
                    isTestingWebhook = uiState.isTestingWebhook,
                    onUrlChange = { viewModel.setWebhookUrl(it) },
                    onTokenChange = { viewModel.setWebhookAuthToken(it) },
                    onToggle = { viewModel.setAutoSendWebhook(it) },
                    onTestClick = { viewModel.testWebhook() },
                    onCancelTestClick = { viewModel.cancelTestWebhook() }
                )
            }

            // Schedule Settings
            item {
                ScheduleCard(
                    frequency = uiState.frequency,
                    scheduleStatus = uiState.scheduleStatus,
                    onFrequencyChange = { viewModel.setFrequency(it) },
                    onSchedule = { viewModel.scheduleExport() },
                    onCancel = { viewModel.cancelSchedule() },
                    autoSendWebhookEvery2Hours = uiState.autoSendWebhookEvery2Hours,
                    webhookUrl = uiState.webhookUrl,
                    onAutoSendEvery2HoursChange = { viewModel.setAutoSendWebhookEvery2Hours(it) }
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
                        onFileClick = { selectedJsonFilePath = it.absolutePath }
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
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                if (uiState.progressTotal > 0) {
                                                    Text(
                                                        stringResource(R.string.export_progress_reading, uiState.progressDate),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    LinearProgressIndicator(
                                                        progress = { uiState.progressCurrent.toFloat() / uiState.progressTotal.toFloat() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        "${uiState.progressCurrent}/${uiState.progressTotal}",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                } else {
                                                    Text(
                                                        stringResource(R.string.export_progress_page, uiState.progressDate, uiState.progressCurrent),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    LinearProgressIndicator(
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
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
