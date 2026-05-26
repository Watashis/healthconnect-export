package com.healthconnect.export.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.time.TimeRangeFilter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.lang.reflect.Field
import com.healthconnect.export.data.HealthDataType
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking

@RunWith(MockitoJUnitRunner.Silent::class)
class HealthConnectRepositoryTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockClient: HealthConnectClient

    private lateinit var repo: HealthConnectRepository

    @Before
    fun setup() {
        repo = HealthConnectRepository(mockContext)

        // Inject mocked client via reflection to bypass HealthConnectClient.getSdkStatus()
        val clientField: Field = HealthConnectRepository::class.java.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(repo, mockClient)
    }

    // ============================
    // filterByPreferredOrigin Tests
    // ============================

    @Test
    fun `filterByPreferredOrigin empty input returns empty`() {
        val result = repo.filterByPreferredOrigin(emptyList<StepsRecord>(), null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterByPreferredOrigin single source returns all records`() {
        val records = listOf(
            mockedStepsRecord(1000, "com.mi.health"),
            mockedStepsRecord(2000, "com.mi.health")
        )
        val result = repo.filterByPreferredOrigin(records, null)
        assertEquals(2, result.size)
    }

    @Test
    fun `filterByPreferredOrigin picks first preferred source`() {
        val records = listOf(
            mockedStepsRecord(100, "com.dummy.other"),
            mockedStepsRecord(200, "com.xiaomi.hm.health"),
            mockedStepsRecord(300, "com.mi.health"),
            mockedStepsRecord(400, "com.dummy.other")
        )
        val result = repo.filterByPreferredOrigin(records, null)
        assertEquals(1, result.size)
        assertEquals(300L, result.single().let { it as StepsRecord }.count)
    }

    @Test
    fun `filterByPreferredOrigin respects preferred packages order`() {
        val records = listOf(
            mockedStepsRecord(100, "com.google.android.apps.fitness"),
            mockedStepsRecord(200, "com.mi.health"),
            mockedStepsRecord(300, "com.xiaomi.hm.health")
        )
        val result = repo.filterByPreferredOrigin(records, null)
        assertEquals(1, result.size)
        assertEquals(200L, result.single().let { it as StepsRecord }.count)
    }

    @Test
    fun `filterByPreferredOrigin user selected source is used`() {
        val records = listOf(
            mockedStepsRecord(100, "com.mi.health"),
            mockedStepsRecord(200, "com.fitbit.FitbitMobile"),
            mockedStepsRecord(300, "com.mobvoi.companion.at")
        )
        val result = repo.filterByPreferredOrigin(
            records,
            selectedSourcePackage = "com.fitbit.FitbitMobile"
        )
        assertEquals(1, result.size)
        assertEquals(200L, result.single().let { it as StepsRecord }.count)
    }

    @Test
    fun `filterByPreferredOrigin selected not found falls back to preferred`() {
        val records = listOf(
            mockedStepsRecord(100, "com.mi.health"),
            mockedStepsRecord(200, "com.fitbit.FitbitMobile")
        )
        val result = repo.filterByPreferredOrigin(
            records,
            selectedSourcePackage = "com.nonexistent.app"
        )
        assertEquals(1, result.size)
        assertEquals(100L, result.single().let { it as StepsRecord }.count)
    }

    @Test
    fun `filterByPreferredOrigin no preferred picks source with most records`() {
        val records = listOf(
            mockedStepsRecord(100, "com.a.other"),
            mockedStepsRecord(200, "com.a.other"),
            mockedStepsRecord(300, "com.b.other"),
            mockedStepsRecord(400, "com.b.other"),
            mockedStepsRecord(500, "com.b.other")
        )
        val result = repo.filterByPreferredOrigin(records, null)
        assertEquals(3, result.size)
        result.forEach {
            assertTrue((it as StepsRecord).count in listOf(300L, 400L, 500L))
        }
    }

    @Test
    fun `filterByPreferredOrigin all unknown uses max records fallback`() {
        val records = listOf(
            mockedStepsRecord(100, null),
            mockedStepsRecord(200, null),
            mockedStepsRecord(300, null)
        )
        val result = repo.filterByPreferredOrigin(records, null)
        assertEquals(3, result.size)
    }

    @Test
    fun `filterByPreferredOrigin multiple sources with same origin all returned`() {
        val records = listOf(
            mockedStepsRecord(1000, "com.mobvoi.companion.at"),
            mockedStepsRecord(2000, "com.mobvoi.companion.at"),
            mockedStepsRecord(3000, "com.mobvoi.companion.at")
        )
        val result = repo.filterByPreferredOrigin(records, null)
        assertEquals(3, result.size)
        assertEquals(listOf(1000L, 2000L, 3000L), result.map { (it as StepsRecord).count })
    }

    @Test
    fun `filterByPreferredOrigin mixed known and unknown picks preferred`() {
        val records = listOf(
            mockedStepsRecord(100, "com.unknown.app"),
            mockedStepsRecord(200, "com.samsung.android.wearable.health"),
            mockedStepsRecord(300, null)
        )
        val result = repo.filterByPreferredOrigin(records, null)
        assertEquals(1, result.size)
        assertEquals(200L, result.single().let { it as StepsRecord }.count)
    }

    // ============================
    // getAvailableSources Tests
    // ============================

    @Test
    fun `getAvailableSources returns sources from steps and heart rate`() {
        runBlocking {
            val stepsRecords = listOf(mockedStepsRecord(1000, "com.mi.health"))
            val hrRecords = listOf(mockedHeartRateRecord("com.fitbit.FitbitMobile"))

            stubClientReadRecords(mockClient, stepsRecords, hrRecords)

            val sources = repo.getAvailableSources()
            assertEquals(2, sources.size)
            assertTrue(sources.contains("com.mi.health"))
            assertTrue(sources.contains("com.fitbit.FitbitMobile"))
        }
    }

    @Test
    fun `getAvailableSources no data returns empty set`() {
        runBlocking {
            stubClientReadRecords(mockClient, emptyList(), emptyList())

            val sources = repo.getAvailableSources()
            assertTrue(sources.isEmpty())
        }
    }

    @Test
    fun `getAvailableSources only steps has data`() {
        runBlocking {
            val stepsRecords = listOf(mockedStepsRecord(1000, "com.mobvoi.companion.at"))
            stubClientReadRecords(mockClient, stepsRecords, emptyList())

            val sources = repo.getAvailableSources()
            assertEquals(1, sources.size)
            assertTrue(sources.contains("com.mobvoi.companion.at"))
        }
    }

    @Test
    fun `getAvailableSources client exception handled gracefully`() {
        runBlocking {
        whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>()))
            .thenThrow(RuntimeException("Network error"))

            val sources = repo.getAvailableSources()
            assertTrue(sources.isEmpty())
        }
    }

    @Test
    fun `getAvailableSources deduplicates sources across data types`() {
        runBlocking {
            val stepsRecords = listOf(mockedStepsRecord(1000, "com.mi.health"))
            val hrRecords = listOf(mockedHeartRateRecord("com.mi.health"))
            stubClientReadRecords(mockClient, stepsRecords, hrRecords)

            val sources = repo.getAvailableSources()
            assertEquals(1, sources.size)
            assertTrue(sources.contains("com.mi.health"))
        }
    }

    // ============================
    // readAllPages Tests
    // ============================

    @Test
    fun `readAllPages single page returns all records`() {
        runBlocking {
            val records = listOf(
                mockedStepsRecord(1000, "com.mi.health"),
                mockedStepsRecord(2000, "com.mi.health")
            )
            stubSinglePage(mockClient, records)

            val request = createStepsRequest()
            val result = repo.readAllPages(request)
            assertEquals(2, result.size)
            assertEquals(listOf(1000L, 2000L), (result as List<StepsRecord>).map { it.count })
        }
    }

    @Test
    fun `readAllPages multiple pages follows pageToken`() {
        runBlocking {
            val page1 = listOf(mockedStepsRecord(100, "com.mi.health"), mockedStepsRecord(200, "com.mi.health"))
            val page2 = listOf(mockedStepsRecord(300, "com.mi.health"), mockedStepsRecord(400, "com.mi.health"))

            stubMultiPage(mockClient, listOf(Pair(page1, "page2"), Pair(page2, null)))

            val request = createStepsRequest()
            val result = repo.readAllPages(request)
            assertEquals(4, result.size)
            assertEquals(listOf(100L, 200L, 300L, 400L), (result as List<StepsRecord>).map { it.count })
            verify(mockClient, times(2)).readRecords(any<ReadRecordsRequest<*>>())
        }
    }

    @Test
    fun `readAllPages empty response returns empty list`() {
        runBlocking {
            stubSinglePage(mockClient, emptyList<StepsRecord>())

            val request = createStepsRequest()
            val result = repo.readAllPages(request)
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `readAllPages client null returns empty`() {
        runBlocking {
            val clientField: Field = HealthConnectRepository::class.java.getDeclaredField("client")
            clientField.isAccessible = true
            clientField.set(repo, null)

            val request = createStepsRequest()
            val result = repo.readAllPages(request)
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `readAllPages three pages concatenates all records`() {
        runBlocking {
            val page1 = listOf(mockedStepsRecord(100, "com.mi.health"))
            val page2 = listOf(mockedStepsRecord(200, "com.mi.health"))
            val page3 = listOf(mockedStepsRecord(300, "com.mi.health"))

            stubMultiPage(mockClient, listOf(
                Pair(page1, "p2"),
                Pair(page2, "p3"),
                Pair(page3, null)
            ))

            val request = createStepsRequest()
            val result = repo.readAllPages(request)
            assertEquals(3, result.size)
            assertEquals(listOf(100L, 200L, 300L), (result as List<StepsRecord>).map { it.count })
            verify(mockClient, times(3)).readRecords(any<ReadRecordsRequest<*>>())
        }
    }

    // ============================
    // getPermissionsForTypes Tests
    // ============================

    @Test
    fun `getPermissionsForTypes empty types returns empty set`() {
        val result = repo.getPermissionsForTypes(emptySet())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPermissionsForTypes single type returns one permission`() {
        val result = repo.getPermissionsForTypes(setOf(HealthDataType.STEPS))
        assertEquals(1, result.size)
        assertTrue(result.first().isNotEmpty())
    }

    @Test
    fun `getPermissionsForTypes multiple types returns all permissions`() {
        val result = repo.getPermissionsForTypes(
            setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE, HealthDataType.SLEEP)
        )
        assertEquals(3, result.size)
        result.forEach { perm ->
            assertTrue(perm.isNotEmpty())
        }
    }

    @Test
    fun `getPermissionsForTypes all types produce unique permissions`() {
        val result = repo.getPermissionsForTypes(HealthDataType.entries.toSet())
        assertEquals(HealthDataType.entries.size, result.size)
    }

    // ============================
    // checkPermissions Tests
    // ============================

    @Test
    fun `checkPermissions client null returns false`() {
        runBlocking {
            val clientField: Field = HealthConnectRepository::class.java.getDeclaredField("client")
            clientField.isAccessible = true
            clientField.set(repo, null)

            val result = repo.checkPermissions(setOf(HealthDataType.STEPS))
            assertFalse(result)
        }
    }

    @Test
    fun `checkPermissions all required granted returns true`() {
        runBlocking {
            val stepsPerm = HealthPermission.getReadPermission(StepsRecord::class)
            val hrPerm = HealthPermission.getReadPermission(HeartRateRecord::class)
            val calPerm = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

            val mockPermController = mock<PermissionController>()
            whenever(mockClient.permissionController).thenReturn(mockPermController)
            whenever(mockPermController.getGrantedPermissions()).thenReturn(
                setOf(stepsPerm, hrPerm, calPerm) // all granted + extra
            )

            val result = repo.checkPermissions(
                setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE)
            )
            assertTrue(result)
        }
    }

    @Test
    fun `checkPermissions some missing returns false`() {
        runBlocking {
            val stepsPerm = HealthPermission.getReadPermission(StepsRecord::class)

            val mockPermController = mock<PermissionController>()
            whenever(mockClient.permissionController).thenReturn(mockPermController)
            whenever(mockPermController.getGrantedPermissions()).thenReturn(
                setOf(stepsPerm)
            )

            val result = repo.checkPermissions(
                setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE)
            )
            assertFalse(result)
        }
    }

    @Test
    fun `checkPermissions no required returns true`() {
        runBlocking {
            val mockPermController = mock<PermissionController>()
            whenever(mockClient.permissionController).thenReturn(mockPermController)
            whenever(mockPermController.getGrantedPermissions()).thenReturn(
                setOf(HealthPermission.getReadPermission(StepsRecord::class))
            )

            val result = repo.checkPermissions(emptySet())
            assertTrue(result)
        }
    }

    // ============================
    // getGrantedPermissions Tests
    // ============================

    @Test
    fun `getGrantedPermissions returns permissions when client exists`() {
        runBlocking {
            val stepsPerm = HealthPermission.getReadPermission(StepsRecord::class)
            val mockPermController = mock<PermissionController>()
            whenever(mockClient.permissionController).thenReturn(mockPermController)
            whenever(mockPermController.getGrantedPermissions()).thenReturn(setOf(stepsPerm))

            val result = repo.getGrantedPermissions()

            assertEquals(setOf(stepsPerm), result)
        }
    }

    @Test
    fun `getGrantedPermissions returns empty when client is null`() {
        runBlocking {
            val clientField: Field = HealthConnectRepository::class.java.getDeclaredField("client")
            clientField.isAccessible = true
            clientField.set(repo, null)

            val result = repo.getGrantedPermissions()

            assertTrue(result.isEmpty())
        }
    }

    // ============================
    // isHealthConnectAvailable Tests
    // ============================

    @Test
    fun `isHealthConnectAvailable returns true when client is not null`() {
        runBlocking {
            val result = repo.isHealthConnectAvailable()
            assertTrue(result)
        }
    }

    @Test
    fun `isHealthConnectAvailable returns false when client is null`() {
        runBlocking {
            val clientField: Field = HealthConnectRepository::class.java.getDeclaredField("client")
            clientField.isAccessible = true
            clientField.set(repo, null)

            val result = repo.isHealthConnectAvailable()
            assertFalse(result)
        }
    }

    // ============================
    // isHealthConnectInstalled Tests
    // ============================

    @Test
    fun `isHealthConnectInstalled returns true when package found`() {
        val mockPm = mock<PackageManager>()
        whenever(mockContext.packageManager).thenReturn(mockPm)
        whenever(mockPm.getPackageInfo(any<String>(), eq(0))).thenReturn(PackageInfo())

        val result = repo.isHealthConnectInstalled()
        assertTrue(result)
    }

    @Test
    fun `isHealthConnectInstalled returns false when package not found`() {
        val mockPm = mock<PackageManager>()
        whenever(mockContext.packageManager).thenReturn(mockPm)
        whenever(mockPm.getPackageInfo(any<String>(), eq(0)))
            .thenThrow(PackageManager.NameNotFoundException())

        val result = repo.isHealthConnectInstalled()
        assertFalse(result)
    }

    // ============================
    // readPeriod Tests
    // ============================

    @Test
    fun `readPeriod single day returns one record`() {
        runBlocking {
            val record = mockedStepsRecord(5000, "com.mi.health")
            val response = mockedResponse(listOf(record), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenReturn(response)

            val result = repo.readPeriod(
                startDate = LocalDate.of(2026, 5, 24),
                endDate = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.STEPS)
            )

            assertEquals(1, result.size)
            assertEquals("2026-05-24", result[0].date)
            assertNotNull(result[0].steps)
            assertEquals(5000L, result[0].steps!!.totalSteps)
        }
    }

    @Test
    fun `readPeriod multiple days returns records for each day`() {
        runBlocking {
            val record = mockedStepsRecord(3000, "com.mi.health")
            val response = mockedResponse(listOf(record), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenReturn(response)

            val result = repo.readPeriod(
                startDate = LocalDate.of(2026, 5, 24),
                endDate = LocalDate.of(2026, 5, 26),
                types = setOf(HealthDataType.STEPS)
            )

            assertEquals(3, result.size)
            assertEquals("2026-05-24", result[0].date)
            assertEquals("2026-05-25", result[1].date)
            assertEquals("2026-05-26", result[2].date)
            result.forEach { record ->
                assertNotNull(record.steps)
                assertEquals(3000L, record.steps!!.totalSteps)
            }
        }
    }

    @Test
    fun `readPeriod startDate after endDate returns empty list`() {
        runBlocking {
            val result = repo.readPeriod(
                startDate = LocalDate.of(2026, 5, 28),
                endDate = LocalDate.of(2026, 5, 26),
                types = setOf(HealthDataType.STEPS)
            )

            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `readPeriod with multiple data types returns complete records`() {
        runBlocking {
            val stepsRecord = mockedStepsRecord(4500, "com.mi.health")
            val hrRecord = mock<HeartRateRecord>()
            whenever(hrRecord.startTime).thenReturn(Instant.now())
            whenever(hrRecord.endTime).thenReturn(Instant.now())
            val metadata = mock<Metadata>()
            whenever(metadata.dataOrigin).thenReturn(DataOrigin("com.mi.health"))
            whenever(hrRecord.metadata).thenReturn(metadata)
            val sample1 = mock<HeartRateRecord.Sample>()
            whenever(sample1.beatsPerMinute).thenReturn(72L)
            whenever(sample1.time).thenReturn(Instant.now())
            val sample2 = mock<HeartRateRecord.Sample>()
            whenever(sample2.beatsPerMinute).thenReturn(68L)
            whenever(sample2.time).thenReturn(Instant.now())
            whenever(hrRecord.samples).thenReturn(listOf(sample1, sample2))

            val stepsResp = mockedResponse(listOf(stepsRecord), null)
            val hrResp = mockedResponse(listOf(hrRecord), null)

            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                when (request.recordType) {
                    StepsRecord::class -> stepsResp
                    HeartRateRecord::class -> hrResp
                    else -> mockedResponse(emptyList<StepsRecord>(), null)
                }
            }

            val result = repo.readPeriod(
                startDate = LocalDate.of(2026, 5, 24),
                endDate = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE)
            )

            assertEquals(1, result.size)
            assertNotNull(result[0].steps)
            assertEquals(4500L, result[0].steps!!.totalSteps)
            assertNotNull(result[0].heartRate)
            assertEquals(70.0, result[0].heartRate!!.avgBpm, 0.01)
        }
    }

    // ============================
    // readDay Tests with different data types
    // ============================

    @Test
    fun `readDay with Sleep data computes total duration and stages`() {
        runBlocking {
            val startTime = Instant.parse("2026-05-24T22:00:00Z")
            val endTime = Instant.parse("2026-05-25T06:30:00Z")

            val stage1 = mock<SleepSessionRecord.Stage>()
            whenever(stage1.stage).thenReturn(2) // Deep sleep
            whenever(stage1.startTime).thenReturn(startTime)
            whenever(stage1.endTime).thenReturn(startTime.plus(90, ChronoUnit.MINUTES))

            val stage2 = mock<SleepSessionRecord.Stage>()
            whenever(stage2.stage).thenReturn(3) // Light sleep
            whenever(stage2.startTime).thenReturn(startTime.plus(90, ChronoUnit.MINUTES))
            whenever(stage2.endTime).thenReturn(endTime)

            val sleepRecord = mock<SleepSessionRecord>()
            whenever(sleepRecord.startTime).thenReturn(startTime)
            whenever(sleepRecord.endTime).thenReturn(endTime)
            whenever(sleepRecord.stages).thenReturn(listOf(stage1, stage2))
            val meta = mock<Metadata>()
            whenever(meta.dataOrigin).thenReturn(null)
            whenever(sleepRecord.metadata).thenReturn(meta)

            val sleepResp = mockedResponse(listOf(sleepRecord), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == SleepSessionRecord::class) sleepResp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.SLEEP)
            )

            assertNotNull(result.sleep)
            assertEquals(510L, result.sleep!!.totalDurationMinutes) // 22:00 to 06:30 = 510 min
            assertTrue(result.sleep!!.sleepStages.containsKey("Deep sleep"))
            assertTrue(result.sleep!!.sleepStages.containsKey("Light sleep"))
            assertEquals(90L, result.sleep!!.sleepStages["Deep sleep"])
        }
    }

    @Test
    fun `readDay with Sleep empty records returns null`() {
        runBlocking {
            val resp = mockedResponse(emptyList<SleepSessionRecord>(), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == SleepSessionRecord::class) resp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.SLEEP)
            )

            assertNull(result.sleep)
        }
    }

    @Test
    fun `readDay with Weight data computes average`() {
        runBlocking {
            val mass1 = mock<Mass>()
            whenever(mass1.inKilograms).thenReturn(75.5)
            val mass2 = mock<Mass>()
            whenever(mass2.inKilograms).thenReturn(80.0)

            val record1 = mock<WeightRecord>()
            whenever(record1.weight).thenReturn(mass1)
            val meta1 = mock<Metadata>()
            whenever(meta1.dataOrigin).thenReturn(null)
            whenever(record1.metadata).thenReturn(meta1)

            val record2 = mock<WeightRecord>()
            whenever(record2.weight).thenReturn(mass2)
            val meta2 = mock<Metadata>()
            whenever(meta2.dataOrigin).thenReturn(null)
            whenever(record2.metadata).thenReturn(meta2)

            val resp = mockedResponse(listOf(record1, record2), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == WeightRecord::class) resp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.WEIGHT)
            )

            assertNotNull(result.weight)
            assertEquals(77.75, result.weight!!.weightKg, 0.01) // (75.5 + 80.0) / 2
            assertEquals(2, result.weight!!.recordsCount)
        }
    }

    @Test
    fun `readDay with Calories data sums with filterByPreferredOrigin`() {
        runBlocking {
            val energy1 = mock<Energy>()
            whenever(energy1.inKilocalories).thenReturn(200.0)
            val energy2 = mock<Energy>()
            whenever(energy2.inKilocalories).thenReturn(300.0)

            val record1 = mock<TotalCaloriesBurnedRecord>()
            whenever(record1.energy).thenReturn(energy1)
            val meta1 = mock<Metadata>()
            whenever(meta1.dataOrigin).thenReturn(DataOrigin("com.mi.health"))
            whenever(record1.metadata).thenReturn(meta1)

            val record2 = mock<TotalCaloriesBurnedRecord>()
            whenever(record2.energy).thenReturn(energy2)
            val meta2 = mock<Metadata>()
            whenever(meta2.dataOrigin).thenReturn(DataOrigin("com.mi.health"))
            whenever(record2.metadata).thenReturn(meta2)

            val resp = mockedResponse(listOf(record1, record2), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == TotalCaloriesBurnedRecord::class) resp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.CALORIES)
            )

            assertNotNull(result.calories)
            assertEquals(500.0, result.calories!!.totalCalories, 0.01)
            assertEquals(2, result.calories!!.recordsCount)
        }
    }

    @Test
    fun `readDay with Speed data computes average from samples`() {
        runBlocking {
            val velocity1 = mock<Velocity>()
            whenever(velocity1.inMetersPerSecond).thenReturn(2.5)
            val velocity2 = mock<Velocity>()
            whenever(velocity2.inMetersPerSecond).thenReturn(3.5)

            val sample1 = mock<SpeedRecord.Sample>()
            whenever(sample1.speed).thenReturn(velocity1)
            whenever(sample1.time).thenReturn(Instant.now())

            val sample2 = mock<SpeedRecord.Sample>()
            whenever(sample2.speed).thenReturn(velocity2)
            whenever(sample2.time).thenReturn(Instant.now().plusSeconds(60))

            val speedRecord = mock<SpeedRecord>()
            whenever(speedRecord.samples).thenReturn(listOf(sample1, sample2))
            val meta = mock<Metadata>()
            whenever(meta.dataOrigin).thenReturn(null)
            whenever(speedRecord.metadata).thenReturn(meta)

            val resp = mockedResponse(listOf(speedRecord), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == SpeedRecord::class) resp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.SPEED)
            )

            assertNotNull(result.speed)
            assertEquals(3.0, result.speed!!.avgSpeedMetersPerSecond!!, 0.01) // (2.5 + 3.5) / 2
            assertEquals(1, result.speed!!.recordsCount)
        }
    }

    @Test
    fun `readDay with Menstruation data returns first record flow`() {
        runBlocking {
            val record = mock<MenstruationFlowRecord>()
            val meta = mock<Metadata>()
            whenever(meta.dataOrigin).thenReturn(null)
            whenever(record.metadata).thenReturn(meta)
            whenever(record.flow).thenReturn(1) // Light
            whenever(record.time).thenReturn(Instant.parse("2026-05-24T08:00:00Z"))

            val resp = mockedResponse(listOf(record), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == MenstruationFlowRecord::class) resp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.MENSTRUATION)
            )

            assertNotNull(result.menstruation)
            assertEquals("Light", result.menstruation!!.flowType)
        }
    }

    @Test
    fun `readDay with Exercise type returns exercise list`() {
        runBlocking {
            val record = mock<ExerciseSessionRecord>()
            val meta = mock<Metadata>()
            whenever(meta.dataOrigin).thenReturn(null)
            whenever(record.metadata).thenReturn(meta)
            whenever(record.exerciseType).thenReturn(48) // Running
            whenever(record.startTime).thenReturn(Instant.parse("2026-05-24T07:00:00Z"))
            whenever(record.endTime).thenReturn(Instant.parse("2026-05-24T07:45:00Z"))
            whenever(record.title).thenReturn("Morning run")
            whenever(record.notes).thenReturn("Felt good")

            val resp = mockedResponse(listOf(record), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == ExerciseSessionRecord::class) resp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.EXERCISE)
            )

            assertNotNull(result.exercises)
            assertEquals(1, result.exercises!!.size)
            assertEquals("Running", result.exercises!![0].exerciseType)
            assertEquals("Morning run", result.exercises!![0].title)
            assertEquals("Felt good", result.exercises!![0].notes)
            assertEquals(45L, result.exercises!![0].durationMinutes)
        }
    }

    @Test
    fun `readDay with Hydration data sums total volume`() {
        runBlocking {
            val volume1 = mock<Volume>()
            whenever(volume1.inLiters).thenReturn(0.5)
            val volume2 = mock<Volume>()
            whenever(volume2.inLiters).thenReturn(1.0)

            val record1 = mock<HydrationRecord>()
            whenever(record1.volume).thenReturn(volume1)
            val meta1 = mock<Metadata>()
            whenever(meta1.dataOrigin).thenReturn(null)
            whenever(record1.metadata).thenReturn(meta1)

            val record2 = mock<HydrationRecord>()
            whenever(record2.volume).thenReturn(volume2)
            val meta2 = mock<Metadata>()
            whenever(meta2.dataOrigin).thenReturn(null)
            whenever(record2.metadata).thenReturn(meta2)

            val resp = mockedResponse(listOf(record1, record2), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                if (request.recordType == HydrationRecord::class) resp
                else mockedResponse(emptyList<StepsRecord>(), null)
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.HYDRATION)
            )

            assertNotNull(result.hydration)
            assertEquals(1.5, result.hydration!!.totalVolumeLiters, 0.01)
        }
    }

    @Test
    fun `readDay with multiple mixed types returns complete record`() {
        runBlocking {
            val stepsRecord = mockedStepsRecord(4500, "com.mi.health")
            val stepsResp = mockedResponse(listOf(stepsRecord), null)

            val mass = mock<Mass>()
            whenever(mass.inKilograms).thenReturn(72.0)
            val weightRecord = mock<WeightRecord>()
            whenever(weightRecord.weight).thenReturn(mass)
            val wMeta = mock<Metadata>()
            whenever(wMeta.dataOrigin).thenReturn(null)
            whenever(weightRecord.metadata).thenReturn(wMeta)
            val weightResp = mockedResponse(listOf(weightRecord), null)

            val energy = mock<Energy>()
            whenever(energy.inKilocalories).thenReturn(2100.0)
            val calRecord = mock<TotalCaloriesBurnedRecord>()
            whenever(calRecord.energy).thenReturn(energy)
            val cMeta = mock<Metadata>()
            whenever(cMeta.dataOrigin).thenReturn(DataOrigin("com.mi.health"))
            whenever(calRecord.metadata).thenReturn(cMeta)
            val calResp = mockedResponse(listOf(calRecord), null)

            val voidResp = mockedResponse(emptyList<StepsRecord>(), null)

            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                when (request.recordType) {
                    StepsRecord::class -> stepsResp
                    WeightRecord::class -> weightResp
                    TotalCaloriesBurnedRecord::class -> calResp
                    else -> voidResp
                }
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.STEPS, HealthDataType.WEIGHT, HealthDataType.CALORIES)
            )

            assertNotNull(result.steps)
            assertEquals(4500L, result.steps!!.totalSteps)
            assertNotNull(result.weight)
            assertEquals(72.0, result.weight!!.weightKg, 0.01)
            assertNotNull(result.calories)
            assertEquals(2100.0, result.calories!!.totalCalories, 0.01)
        }
    }

    @Test
    fun `readDay with empty types returns only metadata`() {
        runBlocking {
            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = emptySet()
            )

            assertEquals("2026-05-24", result.date)
            assertNull(result.steps)
            assertNull(result.heartRate)
            assertNull(result.sleep)
            assertNotNull(result.metadata)
        }
    }

    // ============================
    // createHealthPermissionsIntent Tests
    // ============================

    @Test
    fun `createHealthPermissionsIntent returns non-null intent`() {
        val intent = repo.createHealthPermissionsIntent(
            setOf("android.permission.health.READ_STEPS")
        )
        assertNotNull(intent)
    }

    // ============================
    // createPermissionRequestContract Tests
    // ============================

    @Test
    fun `createPermissionRequestContract returns non-null contract`() {
        whenever(mockContext.packageName).thenReturn("com.healthconnect.export")

        val contract = repo.createPermissionRequestContract()

        assertNotNull(contract)
    }

    // ============================
    // Edge Cases
    // ============================

    @Test
    fun `readAllPages exception propagates`() {
        runBlocking {
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>()))
                .thenThrow(RuntimeException("API failure"))

            val request = createStepsRequest()
            assertThrows(RuntimeException::class.java) {
                runBlocking { repo.readAllPages(request) }
            }
        }
    }

    @Test
    fun `readDay with multiple types where one type has no data`() {
        runBlocking {
            val stepsRecord = mockedStepsRecord(3000, "com.mi.health")
            val stepsResp = mockedResponse(listOf(stepsRecord), null)
            val voidResp = mockedResponse(emptyList<StepsRecord>(), null)

            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
                val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
                when (request.recordType) {
                    StepsRecord::class -> stepsResp
                    DistanceRecord::class -> voidResp
                    FloorsClimbedRecord::class -> voidResp
                    else -> voidResp
                }
            }

            val result = repo.readDay(
                date = LocalDate.of(2026, 5, 24),
                types = setOf(HealthDataType.STEPS, HealthDataType.DISTANCE, HealthDataType.FLOORS_CLIMBED)
            )

            assertNotNull(result.steps)
            assertEquals(3000L, result.steps!!.totalSteps)
            assertNull(result.distance)   // no data → null
            assertNull(result.floorsClimbed) // no data → null
        }
    }

    @Test
    fun `readAllPages respects internal pageSize`() {
        runBlocking {
            val resp = mockedResponse(listOf(mockedStepsRecord(100, "com.mi.health")), null)
            whenever(mockClient.readRecords(any<ReadRecordsRequest<*>>())).thenReturn(resp)

            val request = createStepsRequest()
            val result = repo.readAllPages(request)

            assertEquals(1, result.size)
            // Verify pagination request used correct pageSize
            val captor = argumentCaptor<ReadRecordsRequest<*>>()
            verify(mockClient).readRecords(captor.capture())
            val capturedRequest = captor.firstValue
            assertNotNull(capturedRequest.pageSize)
        }
    }

    // ============================
    // Mock Helpers
    // ============================

    private fun mockedStepsRecord(count: Long, packageName: String?): StepsRecord {
        val record = mock<StepsRecord>()
        whenever(record.count).thenReturn(count)
        whenever(record.startTime).thenReturn(Instant.now())
        whenever(record.endTime).thenReturn(Instant.now().plusSeconds(3600))

        val metadata = mock<Metadata>()
        if (packageName != null) {
            whenever(metadata.dataOrigin).thenReturn(DataOrigin(packageName))
        } else {
            whenever(metadata.dataOrigin).thenReturn(null)
        }
        whenever(record.metadata).thenReturn(metadata)

        return record
    }

    private fun mockedHeartRateRecord(packageName: String): HeartRateRecord {
        val record = mock<HeartRateRecord>()
        whenever(record.startTime).thenReturn(Instant.now())
        whenever(record.endTime).thenReturn(Instant.now())
        whenever(record.samples).thenReturn(emptyList())

        val metadata = mock<Metadata>()
        whenever(metadata.dataOrigin).thenReturn(DataOrigin(packageName))
        whenever(record.metadata).thenReturn(metadata)

        return record
    }

    @Suppress("UNCHECKED_CAST")
    private fun mockedResponse(records: List<*>, pageToken: String?): ReadRecordsResponse<*> {
        val resp = mock<ReadRecordsResponse<*>>()
        whenever(resp.records).thenReturn(records as List<Record>)
        whenever(resp.pageToken).thenReturn(pageToken)
        return resp
    }

    @Suppress("UNCHECKED_CAST")
    private fun createStepsRequest(): ReadRecordsRequest<StepsRecord> {
        val req = mock<ReadRecordsRequest<*>>()
        whenever(req.recordType).thenReturn(StepsRecord::class)
        whenever(req.timeRangeFilter).thenReturn(
            TimeRangeFilter.between(
                Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now()
            )
        )
        whenever(req.dataOriginFilter).thenReturn(emptySet())
        whenever(req.ascendingOrder).thenReturn(true)
        whenever(req.pageSize).thenReturn(5000)
        whenever(req.pageToken).thenReturn(null)
        return req as ReadRecordsRequest<StepsRecord>
    }

    /** Stub client.readRecords with an Answer that dispatches by recordType. */
    private fun stubClientReadRecords(
        client: HealthConnectClient,
        stepsRecords: List<StepsRecord>,
        hrRecords: List<HeartRateRecord>
    ) = runBlocking {
        val stepsResp = mockedResponse(stepsRecords, null)
        val hrResp = mockedResponse(hrRecords, null)

        whenever(client.readRecords(any<ReadRecordsRequest<*>>())).thenAnswer { invocation ->
            val request = invocation.getArgument<ReadRecordsRequest<*>>(0)
            when (request.recordType) {
                StepsRecord::class -> stepsResp
                HeartRateRecord::class -> hrResp
                else -> mockedResponse(emptyList<StepsRecord>(), null)
            }
        }
    }

    /** Stub a single page response (no pagination). */
    private fun stubSinglePage(
        client: HealthConnectClient,
        records: List<StepsRecord>
    ) = runBlocking {
        val resp = mockedResponse(records, null)
        whenever(client.readRecords(any<ReadRecordsRequest<*>>())).thenReturn(resp)
    }

    /** Stub multiple pages — returns responses in order. */
    private fun stubMultiPage(
        client: HealthConnectClient,
        pages: List<Pair<List<StepsRecord>, String?>>
    ) = runBlocking {
        val responses = pages.map { (records, token) ->
            mockedResponse(records, token)
        }
        whenever(client.readRecords(any<ReadRecordsRequest<*>>()))
            .thenReturn(responses[0], *responses.drop(1).toTypedArray())
    }
}
