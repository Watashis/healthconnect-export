package com.healthconnect.export.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Resources
import android.app.ActivityManager
import android.app.job.JobScheduler
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResult
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.Operation
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import com.google.android.gms.tasks.Task
import com.healthconnect.export.data.*
import com.healthconnect.export.repository.*
import com.healthconnect.export.usecase.ExportDataUseCase
import com.healthconnect.export.usecase.ExportStep
import java.io.File
import java.lang.reflect.Field
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner.Silent::class)
class ExportViewModelTest {

    @Mock
    private lateinit var mockHealthRepo: HealthConnectRepository

    @Mock
    private lateinit var mockLocalRepo: LocalExportRepository

    @Mock
    private lateinit var mockDriveRepo: GoogleDriveRepository

    @Mock
    private lateinit var mockWebhookRepo: WebhookRepository

    @Mock
    private lateinit var mockGoogleSignInClient: GoogleSignInClient

    private lateinit var mockApp: Application
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockPrefsEditor: SharedPreferences.Editor
    private lateinit var tempDir: File
    private lateinit var viewModel: ExportViewModel
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    private var mockedGoogleSignIn: MockedStatic<GoogleSignIn>? = null

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        tempDir = createTempDirectory("hce-test-").toFile()

        // Mock SharedPreferences.Editor
        mockPrefsEditor = mock()
        whenever(mockPrefsEditor.putString(any(), anyOrNull<String>())).thenReturn(mockPrefsEditor)
        whenever(mockPrefsEditor.putBoolean(any(), any())).thenReturn(mockPrefsEditor)
        whenever(mockPrefsEditor.putStringSet(any(), anyOrNull())).thenReturn(mockPrefsEditor)
        whenever(mockPrefsEditor.remove(any())).thenReturn(mockPrefsEditor)

        // Mock SharedPreferences
        mockPrefs = mock()
        whenever(mockPrefs.edit()).thenReturn(mockPrefsEditor)
        whenever(mockPrefs.getString(any(), anyOrNull())).thenReturn(null)
        whenever(mockPrefs.getStringSet(any(), anyOrNull())).thenReturn(null)
        whenever(mockPrefs.getBoolean(any(), any())).thenReturn(false)
        whenever(mockPrefs.contains(any())).thenReturn(false)

        // Mock Application — provide stubs for real repos used during ViewModel init
        mockApp = mock()
        whenever(mockApp.applicationContext).thenReturn(mockApp)
        whenever(mockApp.filesDir).thenReturn(tempDir)
        whenever(mockApp.getSharedPreferences(any(), any())).thenReturn(mockPrefs)
        whenever(mockApp.getString(any())).thenReturn("test_string")
        whenever(mockApp.getString(any(), any())).thenReturn("test_string")
        whenever(mockApp.getString(any(), any(), any())).thenReturn("test_string")
        whenever(mockApp.packageName).thenReturn("com.healthconnect.export")
        val mockResources = mock<Resources>()
        whenever(mockApp.resources).thenReturn(mockResources)
        // Required by real repos during init (before we swap with mocks)
        whenever(mockApp.getSystemService(android.content.Context.CONNECTIVITY_SERVICE))
            .thenReturn(mock<android.net.ConnectivityManager>())
        whenever(mockApp.getExternalFilesDir(anyOrNull())).thenReturn(tempDir)
        whenever(mockApp.getExternalFilesDirs(anyOrNull())).thenReturn(arrayOf(tempDir))

        // Initialize WorkManager via WorkManagerTestInitHelper
        // This requires stubbing several system services on the mock Application
        val mockPm = mock<PackageManager>()
        whenever(mockApp.packageManager).thenReturn(mockPm)
        val mockAppInfo = mock<ApplicationInfo>()
        mockAppInfo.processName = "com.healthconnect.export"
        whenever(mockPm.getApplicationInfo(eq("com.healthconnect.export"), any<Int>())).thenReturn(mockAppInfo)
        whenever(mockApp.applicationInfo).thenReturn(mockAppInfo)
        whenever(mockApp.getSystemService(Context.ACTIVITY_SERVICE))
            .thenReturn(mock<ActivityManager>())
        whenever(mockApp.getSystemService(Context.JOB_SCHEDULER_SERVICE))
            .thenReturn(mock<JobScheduler>())
        val dbDir = File(tempDir, "databases")
        dbDir.mkdirs()
        whenever(mockApp.getDatabasePath(any())).thenReturn(File(dbDir, "workmanager.db"))
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(mockApp, config)

        // Mock GoogleSignIn static methods
        mockedGoogleSignIn = Mockito.mockStatic(GoogleSignIn::class.java)
        mockedGoogleSignIn!!.`when`<GoogleSignInClient> {
            GoogleSignIn.getClient(any<Context>(), any<GoogleSignInOptions>())
        }.thenReturn(mockGoogleSignInClient)
        mockedGoogleSignIn!!.`when`<GoogleSignInAccount?> {
            GoogleSignIn.getLastSignedInAccount(any<Context>())
        }.thenReturn(null)

        // Create ViewModel (init block runs here)
        viewModel = ExportViewModel(mockApp)

        // Use test dispatcher for DriveManager's async operations
        viewModel.driveManager.scope = CoroutineScope(testDispatcher)

        // Replace repositories with mocks via reflection
        setField(viewModel, "healthRepo", mockHealthRepo)
        setField(viewModel, "localRepo", mockLocalRepo)
        setField(viewModel.driveManager, "driveRepo", mockDriveRepo)
        setField(viewModel.webhookManager, "webhookRepo", mockWebhookRepo)
        setField(viewModel.webhookManager, "healthRepo", mockHealthRepo)

        // Stub driveRepo.isSignedIn() to return false by default (prevents async Drive calls)
        whenever(mockDriveRepo.isSignedIn()).thenReturn(false)

        // Replace exportUseCase with one that uses mocked repos and test dispatcher
        val useCase = ExportDataUseCase(mockHealthRepo, mockLocalRepo, testDispatcher)
        setField(viewModel, "exportUseCase", useCase)

        // Use testDispatcher for the ViewModel's export scope
        viewModel.exportScope = CoroutineScope(testDispatcher)

        // Use testDispatcher for DriveManager's async operations
        viewModel.driveManager.scope = CoroutineScope(testDispatcher)

        // Clear any messages set during init
        viewModel.clearMessage()
    }

    @After
    fun tearDown() {
        mockedGoogleSignIn?.close()
        mockedGoogleSignIn = null
        Dispatchers.resetMain()
        tempDir.deleteRecursively()
    }

    private fun setField(obj: Any, name: String, value: Any) {
        val field: Field = obj::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(obj, value)
    }

    // =============================================
    // Helper: mock exportUseCase.execute to return a Complete flow
    // =============================================

    private fun mockExportComplete(record: DailyHealthRecord, file: File) {
        val records = listOf(record)
        val files = listOf(file)
        val summary = ExportSummary(
            totalSteps = 1000,
            daysCount = 1,
            startDate = "2026-05-24",
            endDate = "2026-05-24"
        )
        val flow = flowOf<ExportStep>(ExportStep.Complete(records, files, summary))
        val mockUseCase = mock<ExportDataUseCase>()
        whenever(mockUseCase.execute(any(), any(), any(), any())).thenReturn(flow)
        setField(viewModel, "exportUseCase", mockUseCase)
        // Stub listExportedFiles so refreshLocalFiles() doesn't overwrite with empty
        val date = LocalDate.of(2026, 5, 24)
        whenever(mockLocalRepo.listExportedFiles(any())).thenReturn(listOf(date to file))
    }

    // =============================================
    // exportNow() Tests
    // =============================================

    @Test
    fun `exportNow when health not installed shows not installed message`() {
        runTest {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(false)
            whenever(mockHealthRepo.isHealthConnectInstalled()).thenReturn(false)

            viewModel.exportNow()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNotNull(state.message)
        }
    }

    @Test
    fun `exportNow when health not available but installed shows not available message`() {
        runTest {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(false)
            whenever(mockHealthRepo.isHealthConnectInstalled()).thenReturn(true)

            viewModel.exportNow()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNotNull(state.message)
        }
    }

    @Test
    fun `exportNow when no permissions sets pendingPermissions and shows message`() {
        runTest {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(false)
            whenever(mockHealthRepo.getPermissionsForTypes(any())).thenReturn(setOf("perm1", "perm2"))

            viewModel.exportNow()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNotNull(viewModel.pendingPermissions)
            assertEquals(2, viewModel.pendingPermissions?.size)
            assertNotNull(state.message)
        }
    }

    @Test
    fun `exportNow successful export saves files and syncs to drive`() = runTest(testDispatcher) {
        val record = DailyHealthRecord(
            date = "2026-05-24",
            metadata = ExportMetadata(
                appVersion = "1.0.0",
                exportTimestamp = "2026-05-24T12:00:00",
                timezone = "UTC"
            )
        )
        val file = File(tempDir, "health_2026-05-24.json")

        mockExportComplete(record, file)
        whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
        whenever(mockDriveRepo.syncFiles(any<List<File>>())).thenReturn(listOf("file_id"))

        // Set single-day range
        viewModel.setDateRange(LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 24))
        // Re-enable auto-sync for this test
        viewModel.setAutoSyncDrive(true)

        viewModel.exportNow()

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.exportedFiles.size)
        assertNotNull(state.message)

        verify(mockDriveRepo).syncFiles(any<List<File>>())
    }

    @Test
    fun `exportNow when exception occurs shows error message`() {
        runTest {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
                .thenThrow(RuntimeException("Test error"))

            viewModel.exportNow()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNotNull(state.message)
        }
    }

    @Test
    fun `exportNow successful export does not sync drive when autoSync disabled`() {
        runTest {
            val record = DailyHealthRecord(
                date = "2026-05-24",
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-24T12:00:00",
                    timezone = "UTC"
                )
            )
            val file = File(tempDir, "health_2026-05-24.json")

            mockExportComplete(record, file)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(false)

            // Disable auto-sync
            viewModel.setAutoSyncDrive(false)

            // Set single-day range
            viewModel.setDateRange(LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 24))

            viewModel.exportNow()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(1, state.exportedFiles.size)
            assertNotNull(state.message)

            verify(mockDriveRepo, never()).syncFiles(any())
        }
    }

    @Test
    fun `exportNow when webhook enabled sends data to webhook`() {
        runTest {
            val record = DailyHealthRecord(
                date = "2026-05-24",
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-24T12:00:00",
                    timezone = "UTC"
                )
            )
            val records = listOf(record)
            val file = File(tempDir, "health_2026-05-24.json")

            mockExportComplete(record, file)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(false)
            // Stub webhookRepo to return success
            whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull()))
                .thenReturn(WebhookResult.Success(200, ""))

            // Enable webhook
            viewModel.setWebhookUrl("https://example.com/webhook")
            viewModel.setWebhookAuthToken("test-token")
            viewModel.setAutoSendWebhook(true)

            // Set single-day range
            viewModel.setDateRange(LocalDate.of(2026, 5, 24), LocalDate.of(2026, 5, 24))

            viewModel.exportNow()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(1, state.exportedFiles.size)

            verify(mockWebhookRepo).sendRecords(
                eq("https://example.com/webhook"),
                eq(records),
                eq("test-token")
            )
        }
    }

    // =============================================
    // testWebhook() Tests
    // =============================================

    @Test
    fun `testWebhook when url is blank shows enter url message`() {
        runTest {
            // URL is blank by default
            viewModel.testWebhook()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isTestingWebhook)
            assertNotNull(state.message)
            // The message comes from mock string, not actual resource
        }
    }

    @Test
    fun `testWebhook when url has error shows enter url message`() {
        runTest {
            // Set invalid URL — this triggers webhookUrlError
            viewModel.setWebhookUrl("not-a-valid-url")

            // Mock webhook validation to return false
            whenever(mockWebhookRepo.isValidWebhookUrl(any())).thenReturn(false)
            // Re-set to trigger validation with mocked isValidWebhookUrl
            viewModel.setWebhookUrl("not-a-valid-url")

            viewModel.testWebhook()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isTestingWebhook)
            assertNotNull(state.webhookUrlError)
            assertNotNull(state.message)
        }
    }

    @Test
    fun `testWebhook when health data empty shows no data message`() {
        runTest {
            viewModel.setWebhookUrl("https://example.com/webhook")
            whenever(mockWebhookRepo.isValidWebhookUrl(any())).thenReturn(true)
            // Re-set to clear any error
            viewModel.setWebhookUrl("https://example.com/webhook")

            // Mock readPeriodInBatch to return empty list
            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(emptyList())

            viewModel.testWebhook()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isTestingWebhook)
            assertNotNull(state.message)

            verify(mockHealthRepo).readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())
            verify(mockWebhookRepo, never()).sendRecords(any(), any(), anyOrNull())
        }
    }

    @Test
    fun `testWebhook success sends webhook and shows success message`() {
        runTest {
            val record = DailyHealthRecord(
                date = "2026-05-27",
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-27T12:00:00",
                    timezone = "UTC"
                )
            )
            val records = listOf(record)

            viewModel.setWebhookUrl("https://example.com/webhook")
            whenever(mockWebhookRepo.isValidWebhookUrl(any())).thenReturn(true)
            viewModel.setWebhookUrl("https://example.com/webhook")

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(records)
            whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull()))
                .thenReturn(WebhookResult.Success(200, ""))

            viewModel.testWebhook()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isTestingWebhook)
            assertNotNull(state.message)

            verify(mockHealthRepo).readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())
            verify(mockWebhookRepo).sendRecords(
                eq("https://example.com/webhook"),
                eq(records),
                anyOrNull()
            )
        }
    }

    @Test
    fun `testWebhook when sendRecords error shows error message`() {
        runTest {
            val record = DailyHealthRecord(
                date = "2026-05-27",
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-27T12:00:00",
                    timezone = "UTC"
                )
            )
            val records = listOf(record)

            viewModel.setWebhookUrl("https://example.com/webhook")
            whenever(mockWebhookRepo.isValidWebhookUrl(any())).thenReturn(true)
            viewModel.setWebhookUrl("https://example.com/webhook")

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
                .thenReturn(records)
            whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull()))
                .thenReturn(WebhookResult.Error(500, "Internal Server Error"))

            viewModel.testWebhook()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isTestingWebhook)
            assertNotNull(state.message)

            verify(mockHealthRepo).readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())
            verify(mockWebhookRepo).sendRecords(
                eq("https://example.com/webhook"),
                eq(records),
                anyOrNull()
            )
        }
    }

    @Test
    fun `testWebhook exception during read shows error message`() {
        runTest {
            viewModel.setWebhookUrl("https://example.com/webhook")
            whenever(mockWebhookRepo.isValidWebhookUrl(any())).thenReturn(true)
            viewModel.setWebhookUrl("https://example.com/webhook")

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
                .thenThrow(RuntimeException("Network error"))

            viewModel.testWebhook()

            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isTestingWebhook)
            assertNotNull(state.message)

            verify(mockHealthRepo).readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())
        }
    }

    @Test
    fun `cancelTestWebhook cancels running test and resets state`() {
        runTest {
            viewModel.setWebhookUrl("https://example.com/webhook")
            whenever(mockWebhookRepo.isValidWebhookUrl(any())).thenReturn(true)
            viewModel.setWebhookUrl("https://example.com/webhook")

            // testWebhook() sets isTestingWebhook = true synchronously before launching the coroutine
            viewModel.testWebhook()

            // isTestingWebhook is set before the coroutine starts (StandardTestDispatcher delays execution)
            assertTrue(viewModel.uiState.value.isTestingWebhook)

            // Cancel the test before the coroutine executes
            viewModel.cancelTestWebhook()

            val state = viewModel.uiState.value
            assertFalse(state.isTestingWebhook)
            assertNotNull(state.message)
        }
    }

    // =============================================
    // setSourcePackage() Tests
    // =============================================

    @Test
    fun `setSourcePackage updates state and saves to preferences`() {
        viewModel.setSourcePackage("com.test.package")

        val state = viewModel.uiState.value
        assertEquals("com.test.package", state.selectedSourcePackage)
        verify(mockPrefsEditor).putString("selected_source_package", "com.test.package")
        verify(mockPrefsEditor).apply()
    }

    @Test
    fun `setSourcePackage with null clears source`() {
        viewModel.setSourcePackage(null)

        val state = viewModel.uiState.value
        assertNull(state.selectedSourcePackage)
        verify(mockPrefsEditor).putString("selected_source_package", null)
        verify(mockPrefsEditor).apply()
    }

    @Test
    fun `setSourcePackage multiple times updates correctly`() {
        viewModel.setSourcePackage("com.first.pkg")
        assertEquals("com.first.pkg", viewModel.uiState.value.selectedSourcePackage)

        viewModel.setSourcePackage("com.second.pkg")
        assertEquals("com.second.pkg", viewModel.uiState.value.selectedSourcePackage)

        viewModel.setSourcePackage(null)
        assertNull(viewModel.uiState.value.selectedSourcePackage)
    }

    // =============================================
    // handleSignInResult() Tests
    // =============================================

    @Test
    fun `handleSignInResult successful sign-in sets drive connected`() {
        val mockAccount = mock<GoogleSignInAccount>()
        whenever(mockAccount.email).thenReturn("test@example.com")

        val mockTask = mock<Task<GoogleSignInAccount>>()
        whenever(mockTask.getResult(ApiException::class.java)).thenReturn(mockAccount)

        mockedGoogleSignIn!!.`when`<Task<GoogleSignInAccount>> {
            GoogleSignIn.getSignedInAccountFromIntent(any())
        }.thenReturn(mockTask)

        // Stub isSignedIn to return true so refreshDriveStatus doesn't override Connected
        whenever(mockDriveRepo.isSignedIn()).thenReturn(true)

        val intent = mock<Intent>()
        val result = ActivityResult(Activity.RESULT_OK, intent)

        viewModel.handleSignInResult(result)

        val state = viewModel.uiState.value
        assertTrue(state.driveStatus is DriveStatus.Connected)
    }

    @Test
    fun `handleSignInResult api exception sets drive error`() {
        val apiException = ApiException(Status(10, "DEVELOPER_ERROR"))
        val mockTask = mock<Task<GoogleSignInAccount>>()
        whenever(mockTask.getResult(ApiException::class.java)).thenThrow(apiException)

        mockedGoogleSignIn!!.`when`<Task<GoogleSignInAccount>> {
            GoogleSignIn.getSignedInAccountFromIntent(any())
        }.thenReturn(mockTask)

        val intent = mock<Intent>()
        val result = ActivityResult(Activity.RESULT_OK, intent)

        viewModel.handleSignInResult(result)

        val state = viewModel.uiState.value
        assertTrue(state.driveStatus is DriveStatus.Error)
        // The error string comes from mock Application.getString() which returns "test_string"
        assertEquals("test_string", (state.driveStatus as DriveStatus.Error).error)
        assertNotNull(state.message)
    }

    @Test
    fun `handleSignInResult with null account does not update status`() {
        val mockTask = mock<Task<GoogleSignInAccount>>()
        whenever(mockTask.getResult(ApiException::class.java)).thenReturn(null)

        mockedGoogleSignIn!!.`when`<Task<GoogleSignInAccount>> {
            GoogleSignIn.getSignedInAccountFromIntent(any())
        }.thenReturn(mockTask)

        val intent = mock<Intent>()
        val result = ActivityResult(Activity.RESULT_OK, intent)

        viewModel.handleSignInResult(result)

        val state = viewModel.uiState.value
        assertTrue(state.driveStatus is DriveStatus.NotConnected)
    }

    // =============================================
    // Helper: run a block with test dispatcher
    // =============================================

    private fun runTest(testBody: suspend () -> Unit) {
        kotlinx.coroutines.test.runTest(testDispatcher) {
            testBody()
        }
    }
}
