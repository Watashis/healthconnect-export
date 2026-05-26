package com.healthconnect.export.usecase

import android.content.Context
import com.healthconnect.export.data.*
import com.healthconnect.export.repository.HealthConnectRepository
import com.healthconnect.export.repository.LocalExportRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.io.File
import java.time.LocalDate

@RunWith(MockitoJUnitRunner.Silent::class)
class ExportDataUseCaseTest {

    @Mock
    private lateinit var mockHealthRepo: HealthConnectRepository

    @Mock
    private lateinit var mockLocalRepo: LocalExportRepository

    @Mock
    private lateinit var mockContext: Context

    private lateinit var useCase: ExportDataUseCase

    private val defaultConfig = ExportConfig(
        enabledTypes = setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE),
        frequency = ExportFrequency.DAILY,
        autoSyncDrive = false,
        outputDirectory = "HealthConnectExport"
    )

    private val startDate = LocalDate.of(2026, 5, 24)
    private val endDate = LocalDate.of(2026, 5, 25)

    @Before
    fun setup() {
        useCase = ExportDataUseCase(mockHealthRepo, mockLocalRepo)
    }

    // ============================
    // Successful Export
    // ============================

    @Test
    fun `successful export emits Complete with records and files`() {
        runBlocking {
            val records = listOf(
                DailyHealthRecord(
                    date = "2026-05-24",
                    metadata = ExportMetadata(
                        appVersion = "1.0.0",
                        exportTimestamp = "2026-05-24T12:00:00",
                        timezone = "UTC"
                    )
                )
            )
            val files = listOf(File("health_2026-05-24.json"))

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            assertEquals(4, steps.size)
            assertTrue(steps[0] is ExportStep.CheckingPermissions)
            assertTrue(steps[1] is ExportStep.Progress)
            assertEquals("Reading data…", (steps[1] as ExportStep.Progress).message)
            assertTrue(steps[2] is ExportStep.Progress)
            assertEquals("Saving 1 days…", (steps[2] as ExportStep.Progress).message)
            assertTrue(steps[3] is ExportStep.Complete)
            val complete = steps[3] as ExportStep.Complete
            assertEquals(records, complete.records)
            assertEquals(files, complete.files)
        }
    }

    // ============================
    // Health Connect Not Installed
    // ============================

    @Test
    fun `when health not installed emits HealthNotInstalled`() {
        runBlocking {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(false)
            whenever(mockHealthRepo.isHealthConnectInstalled()).thenReturn(false)

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            assertEquals(2, steps.size)
            assertTrue(steps[0] is ExportStep.CheckingPermissions)
            assertTrue(steps[1] is ExportStep.HealthNotInstalled)
        }
    }

    // ============================
    // Health Connect Not Available but Installed
    // ============================

    @Test
    fun `when health not available emits HealthNotAvailable`() {
        runBlocking {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(false)
            whenever(mockHealthRepo.isHealthConnectInstalled()).thenReturn(true)

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            assertEquals(2, steps.size)
            assertTrue(steps[0] is ExportStep.CheckingPermissions)
            assertTrue(steps[1] is ExportStep.HealthNotAvailable)
        }
    }

    // ============================
    // Permissions Required
    // ============================

    @Test
    fun `when permissions missing emits PermissionsRequired`() {
        runBlocking {
            val requiredPermissions = setOf("health_read_steps", "health_read_heart_rate")

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(false)
            whenever(mockHealthRepo.getPermissionsForTypes(any())).thenReturn(requiredPermissions)

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            assertEquals(2, steps.size)
            assertTrue(steps[0] is ExportStep.CheckingPermissions)
            assertTrue(steps[1] is ExportStep.PermissionsRequired)
            assertEquals(requiredPermissions, (steps[1] as ExportStep.PermissionsRequired).permissions)
        }
    }

    // ============================
    // Exception Handling
    // ============================

    @Test
    fun `when exception occurs emits Error`() {
        runBlocking {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull()))
                .thenThrow(RuntimeException("Network failure"))

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            assertEquals(3, steps.size)
            assertTrue(steps[0] is ExportStep.CheckingPermissions)
            assertTrue(steps[1] is ExportStep.Progress)
            assertTrue(steps[2] is ExportStep.Error)
            assertEquals("Network failure", (steps[2] as ExportStep.Error).message)
        }
    }

    // ============================
    // Empty Records
    // ============================

    @Test
    fun `when no data emits Complete with empty records`() {
        runBlocking {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(emptyList())
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(emptyList())

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            assertEquals(4, steps.size)
            assertTrue(steps[0] is ExportStep.CheckingPermissions)
            assertTrue(steps[1] is ExportStep.Progress)
            assertTrue(steps[2] is ExportStep.Progress)
            assertEquals("Saving 0 days…", (steps[2] as ExportStep.Progress).message)
            assertTrue(steps[3] is ExportStep.Complete)
            val complete = steps[3] as ExportStep.Complete
            assertTrue(complete.records.isEmpty())
            assertTrue(complete.files.isEmpty())
            assertEquals(0, complete.summary.daysCount)
            assertEquals(0L, complete.summary.totalSteps)
        }
    }

    // ============================
    // Summary Calculation
    // ============================

    @Test
    fun `summary is correctly computed from records`() {
        runBlocking {
            val record1 = DailyHealthRecord(
                date = "2026-05-24",
                steps = StepsData(totalSteps = 5000, recordsCount = 100),
                heartRate = HeartRateData(avgBpm = 72.0, minBpm = 55, maxBpm = 140, recordsCount = 10),
                calories = CaloriesData(totalCalories = 200.0, recordsCount = 5),
                distance = DistanceData(totalDistanceMeters = 3000.0, recordsCount = 3),
                sleep = SleepData(totalDurationMinutes = 420, sleepStages = mapOf("Deep" to 90), recordsCount = 1),
                activeCalories = ActiveCaloriesData(totalCalories = 150.0, recordsCount = 5),
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-24T12:00:00",
                    timezone = "UTC"
                )
            )

            val record2 = DailyHealthRecord(
                date = "2026-05-25",
                steps = StepsData(totalSteps = 8000, recordsCount = 150),
                heartRate = HeartRateData(avgBpm = 68.0, minBpm = 50, maxBpm = 135, recordsCount = 12),
                calories = CaloriesData(totalCalories = 250.0, recordsCount = 6),
                distance = DistanceData(totalDistanceMeters = 5000.0, recordsCount = 4),
                sleep = SleepData(totalDurationMinutes = 360, sleepStages = mapOf("Light" to 180), recordsCount = 1),
                activeCalories = ActiveCaloriesData(totalCalories = 200.0, recordsCount = 6),
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-25T12:00:00",
                    timezone = "UTC"
                )
            )

            val records = listOf(record1, record2)
            val files = listOf(
                File("health_2026-05-24.json"),
                File("health_2026-05-25.json")
            )

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            val complete = steps.last() as ExportStep.Complete
            val summary = complete.summary

            assertEquals(13000L, summary.totalSteps)
            assertEquals(70.0, summary.avgHeartRate, 0.001)
            assertEquals(450.0, summary.totalCalories, 0.001)
            assertEquals(8000.0, summary.totalDistanceMeters, 0.001)
            assertEquals(390L, summary.avgSleepMinutes) // (420 + 360) / 2
            assertEquals(350.0, summary.totalActiveCalories, 0.001)
            assertEquals(2, summary.daysCount)
            assertEquals("2026-05-24", summary.startDate)
            assertEquals("2026-05-25", summary.endDate)
        }
    }

    // ============================
    // Summary with Missing Fields
    // ============================

    @Test
    fun `summary handles null fields correctly`() {
        runBlocking {
            val record = DailyHealthRecord(
                date = "2026-05-24",
                steps = StepsData(totalSteps = 5000, recordsCount = 100),
                // heartRate = null, calories = null, etc.
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-24T12:00:00",
                    timezone = "UTC"
                )
            )

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(listOf(record))
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(listOf(File("health_2026-05-24.json")))

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            val complete = steps.last() as ExportStep.Complete
            val summary = complete.summary

            assertEquals(5000L, summary.totalSteps)
            assertEquals(0.0, summary.avgHeartRate, 0.001) // null → empty list → 0.0
            assertEquals(0.0, summary.totalCalories, 0.001)
            assertEquals(0.0, summary.totalDistanceMeters, 0.001)
            assertEquals(0L, summary.avgSleepMinutes) // null → empty list → 0L
            assertEquals(0.0, summary.totalActiveCalories, 0.001)
            assertEquals(1, summary.daysCount)
        }
    }

    // ============================
    // Summary with Mixed Null Fields (branch coverage)
    // ============================

    @Test
    fun `summary with mixed null fields covers both branches within sumOf and mapNotNull`() {
        runBlocking {
            // Record 1: steps=null, heartRate=non-null, sleep=null, calories=non-null, distance=null, activeCalories=non-null
            val record1 = DailyHealthRecord(
                date = "2026-05-24",
                heartRate = HeartRateData(avgBpm = 72.0, minBpm = 55, maxBpm = 140, recordsCount = 10),
                calories = CaloriesData(totalCalories = 200.0, recordsCount = 5),
                activeCalories = ActiveCaloriesData(totalCalories = 150.0, recordsCount = 5),
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-24T12:00:00",
                    timezone = "UTC"
                )
            )

            // Record 2: steps=non-null, heartRate=null, sleep=non-null, calories=null, distance=non-null, activeCalories=null
            val record2 = DailyHealthRecord(
                date = "2026-05-25",
                steps = StepsData(totalSteps = 8000, recordsCount = 150),
                sleep = SleepData(totalDurationMinutes = 360, sleepStages = mapOf("Light" to 180), recordsCount = 1),
                distance = DistanceData(totalDistanceMeters = 5000.0, recordsCount = 4),
                metadata = ExportMetadata(
                    appVersion = "1.0.0",
                    exportTimestamp = "2026-05-25T12:00:00",
                    timezone = "UTC"
                )
            )

            val records = listOf(record1, record2)
            val files = listOf(
                File("health_2026-05-24.json"),
                File("health_2026-05-25.json")
            )

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            val complete = steps.last() as ExportStep.Complete
            val summary = complete.summary

            // steps: record1=null → 0, record2=8000 → 8000
            assertEquals(8000L, summary.totalSteps)
            // heartRate: record1=72.0, record2=null → only 72.0 kept
            assertEquals(72.0, summary.avgHeartRate, 0.001)
            // calories: record1=200.0, record2=null → 200.0
            assertEquals(200.0, summary.totalCalories, 0.001)
            // distance: record1=null, record2=5000.0 → 5000.0
            assertEquals(5000.0, summary.totalDistanceMeters, 0.001)
            // sleep: record1=null, record2=360 → only 360 kept
            assertEquals(360L, summary.avgSleepMinutes)
            // activeCalories: record1=150.0, record2=null → 150.0
            assertEquals(150.0, summary.totalActiveCalories, 0.001)
            assertEquals(2, summary.daysCount)
        }
    }

    // ============================
    // Exception with null message
    // ============================

    @Test
    fun `exception with null message shows default error text`() {
        runBlocking {
            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull()))
                .thenThrow(RuntimeException())

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            val error = steps.last() as ExportStep.Error
            assertEquals("Unknown error", error.message)
        }
    }

    // ============================
    // selectedSourcePackage forwarding
    // ============================

    @Test
    fun `selectedSourcePackage is forwarded to readPeriod`() {
        runBlocking {
            val configWithSource = defaultConfig.copy(
                selectedSourcePackage = "com.mi.health"
            )
            val records = listOf(
                DailyHealthRecord(
                    date = "2026-05-24",
                    metadata = ExportMetadata(
                        appVersion = "1.0.0",
                        exportTimestamp = "2026-05-24T12:00:00",
                        timezone = "UTC"
                    )
                )
            )
            val files = listOf(File("health_2026-05-24.json"))

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

            val steps = useCase.execute(mockContext, configWithSource, startDate, endDate).toList()

            assertTrue(steps.last() is ExportStep.Complete)
            verify(mockHealthRepo).readPeriod(
                any(),
                any(),
                any(),
                eq("com.mi.health")
            )
        }
    }

    @Test
    fun `null selectedSourcePackage is forwarded as null to readPeriod`() {
        runBlocking {
            val configWithNullSource = defaultConfig.copy(selectedSourcePackage = null)
            val records = listOf(
                DailyHealthRecord(
                    date = "2026-05-24",
                    metadata = ExportMetadata(
                        appVersion = "1.0.0",
                        exportTimestamp = "2026-05-24T12:00:00",
                        timezone = "UTC"
                    )
                )
            )
            val files = listOf(File("health_2026-05-24.json"))

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), isNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

            val steps = useCase.execute(mockContext, configWithNullSource, startDate, endDate).toList()

            assertTrue(steps.last() is ExportStep.Complete)
            verify(mockHealthRepo).readPeriod(any(), any(), any(), isNull())
        }
    }

    @Test
    fun `selectedSourcePackage with permissions missing still triggers correct flow`() {
        runBlocking {
            val configWithSource = defaultConfig.copy(
                selectedSourcePackage = "com.samsung.samsunghealth"
            )
            val requiredPermissions = setOf("health_read_steps")

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(false)
            whenever(mockHealthRepo.getPermissionsForTypes(any())).thenReturn(requiredPermissions)

            val steps = useCase.execute(mockContext, configWithSource, startDate, endDate).toList()

            assertEquals(2, steps.size)
            assertTrue(steps[0] is ExportStep.CheckingPermissions)
            assertTrue(steps[1] is ExportStep.PermissionsRequired)
            // readPeriod should never be called since permissions are missing
            verify(mockHealthRepo, never()).readPeriod(any(), any(), any(), anyOrNull())
        }
    }

    // ============================
    // Complete step — summary edge cases
    // ============================

    @Test
    fun `complete step contains correct types for all fields`() {
        runBlocking {
            val records = listOf(
                DailyHealthRecord(
                    date = "2026-05-24",
                    steps = StepsData(totalSteps = 7500, recordsCount = 200),
                    heartRate = HeartRateData(avgBpm = 75.0, minBpm = 60, maxBpm = 150, recordsCount = 15),
                    calories = CaloriesData(totalCalories = 300.0, recordsCount = 8),
                    distance = DistanceData(totalDistanceMeters = 4500.0, recordsCount = 5),
                    sleep = SleepData(totalDurationMinutes = 400, sleepStages = mapOf("Deep" to 100), recordsCount = 1),
                    activeCalories = ActiveCaloriesData(totalCalories = 180.0, recordsCount = 6),
                    metadata = ExportMetadata(
                        appVersion = "1.0.0",
                        exportTimestamp = "2026-05-24T12:00:00",
                        timezone = "UTC"
                    )
                )
            )
            val files = listOf(File("health_2026-05-24.json"))

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(files)

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            val complete = steps.last() as ExportStep.Complete
            assertEquals(records, complete.records)
            assertEquals(files, complete.files)
            assertNotNull(complete.summary)
            assertEquals(1, complete.summary.daysCount)
        }
    }

    // ============================
    // Progress Messages
    // ============================

    @Test
    fun `progress messages are emitted in correct order`() {
        runBlocking {
            val records = listOf(
                DailyHealthRecord(
                    date = "2026-05-24",
                    metadata = ExportMetadata(
                        appVersion = "1.0.0",
                        exportTimestamp = "2026-05-24T12:00:00",
                        timezone = "UTC"
                    )
                ),
                DailyHealthRecord(
                    date = "2026-05-25",
                    metadata = ExportMetadata(
                        appVersion = "1.0.0",
                        exportTimestamp = "2026-05-25T12:00:00",
                        timezone = "UTC"
                    )
                ),
                DailyHealthRecord(
                    date = "2026-05-26",
                    metadata = ExportMetadata(
                        appVersion = "1.0.0",
                        exportTimestamp = "2026-05-26T12:00:00",
                        timezone = "UTC"
                    )
                )
            )

            whenever(mockHealthRepo.isHealthConnectAvailable()).thenReturn(true)
            whenever(mockHealthRepo.checkPermissions(any())).thenReturn(true)
            whenever(mockHealthRepo.readPeriod(any(), any(), any(), anyOrNull())).thenReturn(records)
            whenever(mockLocalRepo.saveRecords(any(), any())).thenReturn(
                listOf(File("health_2026-05-24.json"))
            )

            val steps = useCase.execute(mockContext, defaultConfig, startDate, endDate).toList()

            assertEquals(4, steps.size)
            val readingProgress = steps[1] as ExportStep.Progress
            val savingProgress = steps[2] as ExportStep.Progress
            assertEquals("Reading data…", readingProgress.message)
            assertEquals("Saving 3 days…", savingProgress.message)
        }
    }
}
