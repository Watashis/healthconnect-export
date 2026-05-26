package com.healthconnect.export.worker

import android.app.Application
import android.content.Context
import androidx.work.*
import androidx.work.testing.TestListenableWorkerBuilder
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
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.io.File
import java.lang.reflect.Field
import java.time.LocalDate
import java.util.concurrent.TimeUnit

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

    private var mockedWorkManager: MockedStatic<WorkManager>? = null

    @Before
    fun setup() {
        tempDir = createTempDir("hce-worker-test-")

        mockApp = mock()
        whenever(mockApp.applicationContext).thenReturn(mockApp)
        whenever(mockApp.filesDir).thenReturn(tempDir)
        whenever(mockApp.getExternalFilesDir(anyOrNull())).thenReturn(tempDir)
        whenever(mockApp.packageName).thenReturn("com.healthconnect.export")
    }

    @After
    fun tearDown() {
        mockedWorkManager?.close()
        mockedWorkManager = null
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
        whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
        whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
        whenever(mockDriveRepo.uploadFile(any(), any())).thenReturn("file_id")
        whenever(mockWebhookRepo.sendRecords(any(), any(), anyOrNull())).thenReturn(WebhookResult.Success(200, "ok"))

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockLocalRepo).isExported(any(), eq(config))
        verify(mockHealthRepo).readPeriod(any(), any(), eq(config.enabledTypes), anyOrNull())
        verify(mockLocalRepo).saveRecords(eq(records), eq(config))
        verify(mockDriveRepo, atLeastOnce()).uploadFile(any(), any())
        verify(mockWebhookRepo).sendRecords(eq(config.webhookUrl), eq(records), eq(config.webhookAuthToken))
        }
    }

    @Test
    fun `already exported skips health read but syncs to drive`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = true,
            autoSendWebhook = false
        )
        val filePair = LocalDate.now().minusDays(1) to File(tempDir, "health_2026-05-25.json")

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(true)
        whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
        whenever(mockLocalRepo.listExportedFiles(any())).thenReturn(listOf(filePair))
        whenever(mockDriveRepo.uploadFile(any(), any())).thenReturn("file_id")

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        // Should NOT read health data or save
        verify(mockHealthRepo, never()).readPeriod(any(), any(), any(), anyOrNull())
        verify(mockLocalRepo, never()).saveRecords(any(), any())
        // BUT should sync to drive
        verify(mockDriveRepo).uploadFile(any(), any())
        }
    }

    @Test
    fun `already exported without drive sync does nothing`() {
        runBlocking {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(true)

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockHealthRepo, never()).readPeriod(any(), any(), any(), anyOrNull())
        verify(mockLocalRepo, never()).saveRecords(any(), any())
        verify(mockDriveRepo, never()).uploadFile(any(), any())
        verify(mockWebhookRepo, never()).sendRecords(any(), any(), anyOrNull())
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(emptyList())

        val worker = createWorker(config)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockHealthRepo).readPeriod(any(), any(), any(), anyOrNull())
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull()))
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull()))
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull()))
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
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

        whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
        // The default config has ALL types enabled
        whenever(mockHealthRepo.readPeriod(any(), any(), eq(HealthDataType.entries.toSet()), anyOrNull()))
            .thenReturn(records)
        whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)
        // Default autoSyncDrive=true, but drive may or may not be signed in
        whenever(mockDriveRepo.isSignedIn()).thenReturn(false)

        // No config passed → empty input data
        val worker = createWorker(config = null)
        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)

        verify(mockHealthRepo).readPeriod(any(), any(), eq(HealthDataType.entries.toSet()), anyOrNull())
        verify(mockLocalRepo).saveRecords(any(), any())
        }
    }

    // =============================================
    // schedule() tests
    // =============================================

    @Test
    fun `schedule daily enqueues periodic work`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        val mockWorkManager = mock<WorkManager>()

        mockedWorkManager = Mockito.mockStatic(WorkManager::class.java)
        mockedWorkManager!!.`when`<WorkManager> {
            WorkManager.getInstance(any<Context>())
        }.thenReturn(mockWorkManager)

        DailyExportWorker.schedule(mockApp, config)

        val requestCaptor = argumentCaptor<PeriodicWorkRequest>()
        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(DailyExportWorker.WORK_NAME),
            eq(ExistingPeriodicWorkPolicy.KEEP),
            requestCaptor.capture()
        )

        val request = requestCaptor.firstValue
        // Verify the request is a PeriodicWorkRequest
        assertNotNull(request.id)
        // Interval should be 24 hours in milliseconds
        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
    }

    @Test
    fun `schedule weekly enqueues periodic work with 168h interval`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.WEEKLY,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        val mockWorkManager = mock<WorkManager>()

        mockedWorkManager = Mockito.mockStatic(WorkManager::class.java)
        mockedWorkManager!!.`when`<WorkManager> {
            WorkManager.getInstance(any<Context>())
        }.thenReturn(mockWorkManager)

        DailyExportWorker.schedule(mockApp, config)

        val requestCaptor = argumentCaptor<PeriodicWorkRequest>()
        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(DailyExportWorker.WORK_NAME),
            eq(ExistingPeriodicWorkPolicy.KEEP),
            requestCaptor.capture()
        )

        val request = requestCaptor.firstValue
        assertNotNull(request.id)
        assertEquals(TimeUnit.HOURS.toMillis(168), request.workSpec.intervalDuration)
    }

    @Test
    fun `schedule manual cancels existing work`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.MANUAL,
            autoSyncDrive = false,
            autoSendWebhook = false
        )

        val mockWorkManager = mock<WorkManager>()

        mockedWorkManager = Mockito.mockStatic(WorkManager::class.java)
        mockedWorkManager!!.`when`<WorkManager> {
            WorkManager.getInstance(any<Context>())
        }.thenReturn(mockWorkManager)

        DailyExportWorker.schedule(mockApp, config)

        verify(mockWorkManager).cancelUniqueWork(DailyExportWorker.WORK_NAME)
        verify(mockWorkManager, never()).enqueueUniquePeriodicWork(any(), any(), any())
    }

    @Test
    fun `cancel cancels unique work`() {
        val mockWorkManager = mock<WorkManager>()

        mockedWorkManager = Mockito.mockStatic(WorkManager::class.java)
        mockedWorkManager!!.`when`<WorkManager> {
            WorkManager.getInstance(any<Context>())
        }.thenReturn(mockWorkManager)

        DailyExportWorker.cancel(mockApp)

        verify(mockWorkManager).cancelUniqueWork(DailyExportWorker.WORK_NAME)
    }

    @Test
    fun `work name constant is correct`() {
        assertEquals("daily_health_export", DailyExportWorker.WORK_NAME)
    }

    @Test
    fun `getStatus returns live data from work manager`() {
        val mockWorkManager = mock<WorkManager>()
        val mockLiveData = mock<androidx.lifecycle.LiveData<List<WorkInfo>>>()

        mockedWorkManager = Mockito.mockStatic(WorkManager::class.java)
        mockedWorkManager!!.`when`<WorkManager> {
            WorkManager.getInstance(any<Context>())
        }.thenReturn(mockWorkManager)
        whenever(mockWorkManager.getWorkInfosForUniqueWorkLiveData(DailyExportWorker.WORK_NAME))
            .thenReturn(mockLiveData)

        val result = DailyExportWorker.getStatus(mockApp)

        assertSame(mockLiveData, result)
        verify(mockWorkManager).getWorkInfosForUniqueWorkLiveData(DailyExportWorker.WORK_NAME)
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

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
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

    @Test
    fun `drive sync with already exported and no matching files returns success`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = true,
                autoSendWebhook = false
            )

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(true)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
            // No files for yesterday — only files for other dates
            whenever(mockLocalRepo.listExportedFiles(any())).thenReturn(
                listOf(
                    LocalDate.now().minusDays(3) to File(tempDir, "health_old.json")
                )
            )

            val worker = createWorker(config)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(mockLocalRepo).listExportedFiles(any())
            // No files matched yesterday, so no upload
            verify(mockDriveRepo, never()).uploadFile(any(), any())
        }
    }

    @Test
    fun `already exported syncs multiple files to drive`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = true,
                autoSendWebhook = false
            )
            val yesterday = LocalDate.now().minusDays(1)
            val file1 = File(tempDir, "health_1.json")
            val file2 = File(tempDir, "health_2.json")

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(true)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
            whenever(mockLocalRepo.listExportedFiles(any())).thenReturn(
                listOf(yesterday to file1, yesterday to file2)
            )
            whenever(mockDriveRepo.uploadFile(any(), any())).thenReturn("file_id")

            val worker = createWorker(config)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            // Both files should be uploaded
            verify(mockDriveRepo, times(2)).uploadFile(any(), any())
            verify(mockDriveRepo).uploadFile(eq(file1), any())
            verify(mockDriveRepo).uploadFile(eq(file2), any())
        }
    }

    @Test
    fun `drive sync not signed in skips upload for already exported`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = true,
                autoSendWebhook = false
            )

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(true)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(false)

            val worker = createWorker(config)
            val result = worker.doWork()

            assertEquals(ListenableWorker.Result.success(), result)
            verify(mockDriveRepo).isSignedIn()
            verify(mockDriveRepo, never()).uploadFile(any(), any())
            verify(mockLocalRepo, never()).listExportedFiles(any())
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

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
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

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
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

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
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

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(false)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
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

    @Test
    fun `sync to drive exception on already exported causes retry`() {
        runBlocking {
            val config = ExportConfig(
                enabledTypes = setOf(HealthDataType.STEPS),
                frequency = ExportFrequency.DAILY,
                autoSyncDrive = true,
                autoSendWebhook = false
            )
            val yesterday = LocalDate.now().minusDays(1)
            val file1 = File(tempDir, "health_yesterday.json")

            whenever(mockLocalRepo.isExported(any(), any())).thenReturn(true)
            whenever(mockDriveRepo.isSignedIn()).thenReturn(true)
            whenever(mockLocalRepo.listExportedFiles(any())).thenReturn(
                listOf(yesterday to file1)
            )
            whenever(mockDriveRepo.uploadFile(any(), any()))
                .thenThrow(RuntimeException("Drive error"))

            val worker = createWorker(config)
            val result = worker.doWork()

            // syncToDrive exception propagates to catch → retry
            assertEquals(ListenableWorker.Result.retry(), result)
            verify(mockDriveRepo).uploadFile(any(), any())
        }
    }
}
