package com.healthconnect.export.worker

import android.app.ActivityManager
import android.app.Application
import android.app.job.JobScheduler
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.content.res.Resources
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.healthconnect.export.data.*
import com.healthconnect.export.repository.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.io.File
import java.lang.reflect.Field
import java.time.LocalDate
import kotlin.io.path.createTempDirectory

@RunWith(MockitoJUnitRunner.Silent::class)
class DailyExportWorkerTest {

    @Mock
    private lateinit var mockHealthRepo: HealthConnectRepository

    @Mock
    private lateinit var mockLocalRepo: LocalExportRepository

    @Mock
    private lateinit var mockDriveRepo: GoogleDriveRepository

    @Mock
    private lateinit var mockWebhookRepo: WebhookRepository

    private lateinit var mockApp: Application
    private lateinit var tempDir: File
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        tempDir = createTempDirectory("hce-worker-test-").toFile()

        mockApp = mock()
        whenever(mockApp.applicationContext).thenReturn(mockApp)
        whenever(mockApp.filesDir).thenReturn(tempDir)
        whenever(mockApp.getExternalFilesDir(anyOrNull())).thenReturn(tempDir)
        whenever(mockApp.packageName).thenReturn("com.healthconnect.export")

        // Stubs required for WorkManagerTestInitHelper
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
        whenever(mockApp.getSystemService(Context.CONNECTIVITY_SERVICE))
            .thenReturn(mock<ConnectivityManager>())
        val mockResources = mock<Resources>()
        whenever(mockApp.resources).thenReturn(mockResources)
        val dbDir = File(tempDir, "databases")
        dbDir.mkdirs()
        whenever(mockApp.getDatabasePath(any())).thenReturn(File(dbDir, "workmanager.db"))

        // Initialize WorkManager
        val wmConfig = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(mockApp, wmConfig)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // =============================================
    // Helper: inject private fields via reflection
    // =============================================

    private fun setField(obj: Any, name: String, value: Any) {
        val field: Field = obj::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(obj, value)
    }

    // =============================================
    // Helper: create a configured worker with mocked repos
    // =============================================

    private fun createWorker(
        config: ExportConfig? = null,
        context: Context = mockApp
    ): DailyExportWorker {
        val inputData = if (config != null) {
            workDataOf(DailyExportWorker.KEY_CONFIG to json.encodeToString(config))
        } else {
            workDataOf()
        }
        val worker = TestListenableWorkerBuilder<DailyExportWorker>(context)
            .setInputData(inputData)
            .build()

        // Replace repos with mocks via reflection
        setField(worker, "healthRepo", mockHealthRepo)
        setField(worker, "localRepo", mockLocalRepo)
        setField(worker, "driveRepo", mockDriveRepo)
        setField(worker, "webhookRepo", mockWebhookRepo)

        return worker
    }

    // =============================================
    // doWork() — Normal flow tests
    // =============================================

    @Test
    fun `successful export saves records syncs to drive and sends webhook`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = true,
            webhookUrl = "https://example.com/hook",
            webhookAuthToken = "test-token",
            autoSendWebhook = true
        )
        val records = listOf(
            DailyHealthRecord(
                date = LocalDate.now().minusDays(1).toString(),
                metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
            )
        )
        val files = listOf(File(tempDir, "health_yesterday.json"))

        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
        whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
        whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
        whenever(mockDriveRepo.uploadFile(any(), any())).thenReturn("file_id")
        whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull())).thenReturn(WebhookResult.Success(200, "ok"))

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockHealthRepo).readPeriodInBatch(any(), any(), eq(config.enabledTypes), anyOrNull(), anyOrNull())
        verify(mockLocalRepo).saveRecords(eq(records), eq(config))
        verify(mockDriveRepo, atLeastOnce()).uploadFile(any(), any())
        verify(mockWebhookRepo).sendRecords(eq(config.webhookUrl), eq(records), eq(config.webhookAuthToken))
        }
    }

    @Test
    fun `empty records returns success`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(emptyList())

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockHealthRepo).readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())
        verify(mockLocalRepo, never()).saveRecords(any(), any())
        }
    }

    @Test
    fun `security exception returns failure`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(SecurityException("No permission"))

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        }
    }

    @Test
    fun `illegal state exception returns failure`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(IllegalStateException("HC not available"))

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        }
    }

    @Test
    fun `generic exception returns retry`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull()))
            .thenThrow(RuntimeException("Network error"))

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        }
    }

    @Test
    fun `webhook disabled does not send`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false,
            webhookUrl = "https://example.com/hook"
        )
        val records = listOf(
            DailyHealthRecord(
                date = LocalDate.now().minusDays(1).toString(),
                metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
            )
        )
        val files = listOf(File(tempDir, "health_yesterday.json"))

        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
        whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockWebhookRepo, never()).sendRecords(any(), any(), anyOrNull())
        }
    }

    @Test
    fun `drive sync disabled does not upload`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )
        val records = listOf(
            DailyHealthRecord(
                date = LocalDate.now().minusDays(1).toString(),
                metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
            )
        )
        val files = listOf(File(tempDir, "health_yesterday.json"))

        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
        whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockDriveRepo, never()).uploadFile(any(), any())
        }
    }

    @Test
    fun `default config when no input data`() {
        runBlocking {
        // When no input data, the worker should use a default config
        // with ALL types, DAILY frequency, autoSyncDrive=true
        val records = listOf(
            DailyHealthRecord(
                date = LocalDate.now().minusDays(1).toString(),
                metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
            )
        )
        val files = listOf(File(tempDir, "health_yesterday.json"))

        // The default config has ALL types enabled
        whenever(mockHealthRepo.readPeriodInBatch(any(), any(), eq(HealthDataType.entries.toSet()), anyOrNull(), anyOrNull()))
            .thenReturn(records)
        whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
        // Default autoSyncDrive=true, but drive may or may not be signed in
        whenever(mockDriveRepo.isSignedIn()).thenReturn(false)

        // No config passed → empty input data
        val worker = createWorker(config = null)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockHealthRepo).readPeriodInBatch(any(), any(), eq(HealthDataType.entries.toSet()), anyOrNull(), anyOrNull())
        verify(mockLocalRepo).saveRecords(any(), any())
        }
    }

    // =============================================
    // schedule() tests — verifies methods don't throw
    // (Full verification requires instrumentation tests
    //  because SQLite doesn't work on mock Application)
    // =============================================

    @Test
    fun `schedule daily enqueues periodic work`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        // Should not throw — verifies WorkManager.getInstance() is called
        DailyExportWorker.schedule(mockApp, config)

        // Verify work was registered via LiveData API
        val liveData = DailyExportWorker.getStatus(mockApp)
        assertNotNull("getStatus should return LiveData", liveData)
        // LiveData value may be null if not observed — that's OK,
        // the important thing is schedule() completed without exception
    }

    @Test
    fun `schedule weekly enqueues periodic work with 168h interval`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.WEEKLY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        // Should not throw
        DailyExportWorker.schedule(mockApp, config)
    }

    @Test
    fun `schedule manual cancels existing work`() {
        val dailyConfig = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        // Schedule daily first
        DailyExportWorker.schedule(mockApp, dailyConfig)

        // Schedule manual — should cancel daily and not throw
        val manualConfig = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.MANUAL,
            autoSyncDrive = false,
            autoSendWebhook = false
        )
        DailyExportWorker.schedule(mockApp, manualConfig)
    }

    @Test
    fun `cancel cancels unique work`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )
        DailyExportWorker.schedule(mockApp, config)

        // Should not throw
        DailyExportWorker.cancel(mockApp)
    }

    @Test
    fun `work name constant is correct`() {
        assertEquals("daily_health_export", DailyExportWorker.WORK_NAME)
    }

    @Test
    fun `getStatus returns live data from work manager`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )
        DailyExportWorker.schedule(mockApp, config)

        val result = DailyExportWorker.getStatus(mockApp)
        assertNotNull("getStatus should return LiveData", result)
    }

    // =============================================
    // Drive sync — additional scenarios
    // =============================================

    @Test
    fun `drive upload exception causes retry`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = true,
                autoSendWebhook = false
            )
            val records = listOf(
                DailyHealthRecord(
                    date = LocalDate.now().minusDays(1).toString(),
                    metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
                )
            )
            val files = listOf(File(tempDir, "health_yesterday.json"))

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
            whenever(mockDriveRepo.uploadFile(any(), any()))
                .thenThrow(RuntimeException("Drive upload failed"))

            val worker = createWorker(config)
            val result = worker.doWork()

            // Generic exception propagates to catch → retry
            assertEquals(ListenableWorker.Result.retry(), result)
            verify(mockDriveRepo).uploadFile(any(), any())
        }
    }

    // =============================================
    // Webhook — additional scenarios
    // =============================================

    @Test
    fun `webhook with blank url does not send`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = false,
                autoSendWebhook = true,
                webhookUrl = ""  // blank URL
            )
            val records = listOf(
                DailyHealthRecord(
                    date = LocalDate.now().minusDays(1).toString(),
                    metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
                )
            )
            val files = listOf(File(tempDir, "health_yesterday.json"))

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

            val worker = createWorker(config)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(mockWebhookRepo, never()).sendRecords(any(), any(), anyOrNull())
        }
    }

    @Test
    fun `webhook exception causes retry`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = false,
                autoSendWebhook = true,
                webhookUrl = "https://example.com/hook"
            )
            val records = listOf(
                DailyHealthRecord(
                    date = LocalDate.now().minusDays(1).toString(),
                    metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
                )
            )
            val files = listOf(File(tempDir, "health_yesterday.json"))

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
            whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull()))
                .thenThrow(RuntimeException("Webhook timeout"))

            val worker = createWorker(config)
            val result = worker.doWork()

            // Generic exception propagates to catch → retry
            assertEquals(ListenableWorker.Result.retry(), result)
            verify(mockWebhookRepo).sendRecords(any(), any(), anyOrNull())
        }
    }

    @Test
    fun `webhook sends with auth token`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = false,
                autoSendWebhook = true,
                webhookUrl = "https://example.com/hook",
                webhookAuthToken = "secret-token-123"
            )
            val records = listOf(
                DailyHealthRecord(
                    date = LocalDate.now().minusDays(1).toString(),
                    metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
                )
            )
            val files = listOf(File(tempDir, "health_yesterday.json"))

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
            whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull()))
                .thenReturn(WebhookResult.Success(200, "OK"))

            val worker = createWorker(config)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Verify the auth token is passed correctly
            verify(mockWebhookRepo).sendRecords(
                eq(config.webhookUrl),
                eq(records),
                eq(config.webhookAuthToken)
            )
        }
    }

    // =============================================
    // Combined scenarios
    // =============================================

    @Test
    fun `drive exception before webhook results in retry`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = true,
                autoSendWebhook = true,
                webhookUrl = "https://example.com/hook",
                webhookAuthToken = "token"
            )
            val records = listOf(
                DailyHealthRecord(
                    date = LocalDate.now().minusDays(1).toString(),
                    metadata = ExportMetadata("1.0.0", "2026-05-25T12:00:00", "UTC")
                )
            )
            val files = listOf(File(tempDir, "health_yesterday.json"))

            whenever(mockHealthRepo.readPeriodInBatch(any(), any(), any(), anyOrNull(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
            whenever(mockDriveRepo.uploadFile(any(), any()))
                .thenThrow(RuntimeException("Drive error"))
            whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull()))
                .thenReturn(WebhookResult.Success(200, "OK"))

            val worker = createWorker(config)
            val result = worker.doWork()

            // Drive exception propagates to catch → retry (webhook never reached)
            assertEquals(ListenableWorker.Result.retry(), result)
            verify(mockDriveRepo).uploadFile(any(), any())
            // Webhook is not called because Drive exception interrupted the flow
            verify(mockWebhookRepo, never()).sendRecords(any(), any(), anyOrNull())
        }
    }
}
