package com.healthconnect.export.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for serialization of data models in DataModels.kt.
 *
 * Tests cover:
 * - DailyHealthRecord full roundtrip (serialize → deserialize → compare)
 * - DailyHealthRecord minimal record (only required fields)
 * - DailyHealthRecord deserialization from known JSON string
 * - ExportConfig roundtrip with various configurations
 * - ExportSummary construction and default values
 * - Enum serialization (ExportFrequency, HealthDataType)
 * - sourceDisplayName helper
 * - Instant extension functions
 */
class DataModelsSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // =============================================
    // DailyHealthRecord serialization
    // =============================================

    @Test
    fun `DailyHealthRecord full roundtrip preserves all fields`() {
        val original = DailyHealthRecord(
            date = "2026-06-01",
            steps = StepsData(totalSteps = 10000, recordsCount = 400),
            heartRate = HeartRateData(avgBpm = 72.5, minBpm = 55, maxBpm = 142, recordsCount = 18),
            sleep = SleepData(
                totalDurationMinutes = 420,
                sleepStages = mapOf("Deep sleep" to 90, "Light sleep" to 195, "REM sleep" to 105, "Awake" to 30),
                recordsCount = 1
            ),
            calories = CaloriesData(totalCalories = 2150.0, recordsCount = 180),
            distance = DistanceData(totalDistanceMeters = 8234.5, recordsCount = 620),
            floorsClimbed = FloorsClimbedData(totalFloors = 12.0, recordsCount = 15),
            activeCalories = ActiveCaloriesData(totalCalories = 450.0, recordsCount = 60),
            weight = WeightData(weightKg = 78.5, recordsCount = 1),
            bodyFat = BodyFatData(percentage = 18.5, recordsCount = 1),
            bloodPressure = BloodPressureData(
                systolicMmHg = 120.0, diastolicMmHg = 80.0,
                bodyPosition = "Sitting", recordsCount = 3
            ),
            bloodGlucose = BloodGlucoseData(
                level = 5.8, specimenSource = "Capillary blood",
                mealType = "Fasting", recordsCount = 4
            ),
            oxygenSaturation = OxygenSaturationData(percentage = 97.5, recordsCount = 1),
            bodyTemperature = BodyTemperatureData(
                temperatureCelsius = 36.6, measurementLocation = "Axillary", recordsCount = 1
            ),
            respiratoryRate = RespiratoryRateData(rate = 16.0, recordsCount = 6),
            hydration = HydrationData(totalVolumeLiters = 2.5, recordsCount = 8),
            restingHeartRate = RestingHeartRateData(avgBpm = 65.0, minBpm = 62, maxBpm = 68, recordsCount = 1),
            exercises = listOf(
                ExerciseData("Running", "07:30", "08:15", 45, title = "Morning run")
            ),
            nutrition = listOf(
                NutritionData(name = "Apple", mealType = "Snack", energyKcal = 95.0)
            ),
            menstruation = MenstruationData(flowType = "Medium", time = "2026-06-01T08:00:00"),
            speed = SpeedData(avgSpeedMetersPerSecond = 1.5, recordsCount = 420),
            metadata = ExportMetadata("1.0.0", "2026-06-01T23:00:00", "Europe/Moscow", "pixel_9")
        )

        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<DailyHealthRecord>(jsonString)

        assertEquals(original.date, restored.date)
        assertEquals(original.steps, restored.steps)
        assertEquals(original.heartRate, restored.heartRate)
        assertEquals(original.sleep, restored.sleep)
        assertEquals(original.calories, restored.calories)
        assertEquals(original.distance, restored.distance)
        assertEquals(original.floorsClimbed, restored.floorsClimbed)
        assertEquals(original.activeCalories, restored.activeCalories)
        assertEquals(original.weight, restored.weight)
        assertEquals(original.bodyFat, restored.bodyFat)
        assertEquals(original.bloodPressure, restored.bloodPressure)
        assertEquals(original.bloodGlucose, restored.bloodGlucose)
        assertEquals(original.oxygenSaturation, restored.oxygenSaturation)
        assertEquals(original.bodyTemperature, restored.bodyTemperature)
        assertEquals(original.respiratoryRate, restored.respiratoryRate)
        assertEquals(original.hydration, restored.hydration)
        assertEquals(original.restingHeartRate, restored.restingHeartRate)
        assertEquals(original.exercises, restored.exercises)
        assertEquals(original.nutrition, restored.nutrition)
        assertEquals(original.menstruation, restored.menstruation)
        assertEquals(original.speed, restored.speed)
        assertEquals(original.metadata, restored.metadata)
    }

    @Test
    fun `DailyHealthRecord minimal record has null optional fields`() {
        val record = DailyHealthRecord(
            date = "2026-06-01",
            metadata = ExportMetadata("1.0.0", "2026-06-01T23:00:00", "UTC")
        )

        val jsonString = json.encodeToString(record)
        val restored = json.decodeFromString<DailyHealthRecord>(jsonString)

        assertEquals("2026-06-01", restored.date)
        assertNull(restored.steps)
        assertNull(restored.heartRate)
        assertNull(restored.sleep)
        assertNull(restored.calories)
        assertNull(restored.distance)
        assertNull(restored.weight)
        assertNull(restored.exercises)
        assertNull(restored.nutrition)
        assertEquals("1.0.0", restored.metadata.appVersion)
    }

    @Test
    fun `DailyHealthRecord deserialization from JSON string`() {
        val rawJson = """
        {
            "date": "2026-06-01",
            "steps": { "total_steps": 8500, "records_count": 320 },
            "heart_rate": { "avg_bpm": 70.0, "min_bpm": 58, "max_bpm": 130, "records_count": 15 },
            "sleep": {
                "total_duration_minutes": 390,
                "sleep_stages": { "Deep sleep": 80, "Light sleep": 210, "REM sleep": 85, "Awake": 15 },
                "records_count": 1
            },
            "metadata": {
                "app_version": "1.0.0",
                "export_timestamp": "2026-06-01T23:00:00",
                "timezone": "Europe/Moscow",
                "source_device": "test"
            }
        }
        """.trimIndent()

        val record = json.decodeFromString<DailyHealthRecord>(rawJson)

        assertEquals("2026-06-01", record.date)
        assertEquals(8500L, record.steps?.totalSteps)
        assertNotNull(record.heartRate)
        assertEquals(70.0, record.heartRate!!.avgBpm, 0.001)
        assertNotNull(record.sleep)
        assertEquals(390, record.sleep!!.totalDurationMinutes)
        assertTrue(record.sleep?.sleepStages?.containsKey("Deep sleep") == true)
        assertNull(record.calories)
        assertNull(record.distance)
        assertEquals("test", record.metadata.sourceDevice)
    }

    @Test
    fun `DailyHealthRecord JSON field names use SerialName annotations`() {
        val record = DailyHealthRecord(
            date = "2026-06-01",
            steps = StepsData(5000, 200),
            heartRate = HeartRateData(75.0, 60, 140, 10),
            metadata = ExportMetadata("1.0.0", "2026-06-01T23:00:00", "UTC")
        )

        val jsonString = json.encodeToString(record)
        val parsed = json.parseToJsonElement(jsonString).jsonObject

        // Verify SerialName mappings
        assertTrue(parsed.containsKey("heart_rate"))
        assertFalse(parsed.containsKey("heartRate"))

        // Steps nested field
        val stepsObj = parsed["steps"]!!.jsonObject
        assertTrue(stepsObj.containsKey("total_steps"))
        assertFalse(stepsObj.containsKey("totalSteps"))

        // Heart rate nested fields
        val hrObj = parsed["heart_rate"]!!.jsonObject
        assertTrue(hrObj.containsKey("avg_bpm"))
        assertTrue(hrObj.containsKey("min_bpm"))
        assertTrue(hrObj.containsKey("max_bpm"))
    }

    @Test
    fun `DailyHealthRecord with null exercises and nutrition fields`() {
        val record = DailyHealthRecord(
            date = "2026-06-01",
            metadata = ExportMetadata("1.0.0", "2026-06-01T23:00:00", "UTC")
        )

        // When encodeDefaults = false, null lists should still be present or absent
        // With encodeDefaults = true, null fields are serialized as null
        val jsonString = json.encodeToString(record)

        // Decode with ignoreUnknownKeys — should work with or without null fields
        val restored = json.decodeFromString<DailyHealthRecord>(jsonString)
        assertNull(restored.exercises)
        assertNull(restored.nutrition)
    }

    // =============================================
    // ExportConfig serialization
    // =============================================

    @Test
    fun `ExportConfig full roundtrip preserves all fields`() {
        val original = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS, HealthDataType.HEART_RATE, HealthDataType.SLEEP),
            frequency = ExportFrequency.WEEKLY,
            autoSyncDrive = true,
            webhookUrl = "https://example.com/hook",
            webhookAuthToken = "secret-token",
            autoSendWebhook = true,
            outputDirectory = "CustomExport",
            selectedSourcePackage = "com.test.package"
        )

        val jsonString = json.encodeToString(original)
        val restored = json.decodeFromString<ExportConfig>(jsonString)

        assertEquals(original.enabledTypes, restored.enabledTypes)
        assertEquals(original.frequency, restored.frequency)
        assertEquals(original.autoSyncDrive, restored.autoSyncDrive)
        assertEquals(original.webhookUrl, restored.webhookUrl)
        assertEquals(original.webhookAuthToken, restored.webhookAuthToken)
        assertEquals(original.autoSendWebhook, restored.autoSendWebhook)
        assertEquals(original.outputDirectory, restored.outputDirectory)
        assertEquals(original.selectedSourcePackage, restored.selectedSourcePackage)
    }

    @Test
    fun `ExportConfig default values are used when fields missing`() {
        val minimalJson = """
        {
            "enabledTypes": ["STEPS"],
            "frequency": "DAILY",
            "autoSyncDrive": false
        }
        """.trimIndent()

        val config = json.decodeFromString<ExportConfig>(minimalJson)

        assertEquals(setOf(HealthDataType.STEPS), config.enabledTypes)
        assertEquals(ExportFrequency.DAILY, config.frequency)
        assertFalse(config.autoSyncDrive)
        assertEquals("", config.webhookUrl)
        assertEquals("", config.webhookAuthToken)
        assertFalse(config.autoSendWebhook)
        assertEquals("HealthConnectExport", config.outputDirectory)
        assertNull(config.selectedSourcePackage)
    }

    @Test
    fun `ExportConfig with all health data types serializes set`() {
        val config = ExportConfig(
            enabledTypes = HealthDataType.entries.toSet(),
            frequency = ExportFrequency.DAILY,
            autoSyncDrive = true
        )

        val jsonString = json.encodeToString(config)
        val restored = json.decodeFromString<ExportConfig>(jsonString)

        assertEquals(HealthDataType.entries.size, restored.enabledTypes.size)
        assertTrue(restored.enabledTypes.containsAll(HealthDataType.entries))
    }

    @Test
    fun `ExportConfig with single type serializes correctly`() {
        val config = ExportConfig(
            enabledTypes = setOf(HealthDataType.STEPS),
            frequency = ExportFrequency.MANUAL,
            autoSyncDrive = false
        )

        val jsonString = json.encodeToString(config)
        assertTrue(jsonString.contains("STEPS"))
        assertTrue(jsonString.contains("MANUAL"))
        assertFalse(jsonString.contains("HEART_RATE"))

        val restored = json.decodeFromString<ExportConfig>(jsonString)
        assertEquals(setOf(HealthDataType.STEPS), restored.enabledTypes)
    }

    // =============================================
    // ExportFrequency serialization
    // =============================================

    @Test
    fun `ExportFrequency enum values serialize and deserialize`() {
        for (freq in ExportFrequency.entries) {
            val jsonString = json.encodeToString(ExportFrequency.serializer(), freq)
            val restored = json.decodeFromString<ExportFrequency>(jsonString)
            assertEquals(freq, restored)
        }
    }

    @Test
    fun `ExportFrequency display name and hours are correct`() {
        assertEquals("Manual", ExportFrequency.MANUAL.displayName)
        assertEquals(0L, ExportFrequency.MANUAL.hours)

        assertEquals("Daily", ExportFrequency.DAILY.displayName)
        assertEquals(24L, ExportFrequency.DAILY.hours)

        assertEquals("Weekly", ExportFrequency.WEEKLY.displayName)
        assertEquals(168L, ExportFrequency.WEEKLY.hours)
    }

    @Test
    fun `ExportFrequency JSON uses enum name`() {
        val jsonString = json.encodeToString(ExportFrequency.serializer(), ExportFrequency.DAILY)
        // Should produce "DAILY" (quoted string), not an object
        assertEquals("\"DAILY\"", jsonString.trim())
    }

    // =============================================
    // HealthDataType serialization
    // =============================================

    @Test
    fun `HealthDataType all entries have unique display names`() {
        val names = HealthDataType.entries.map { it.displayName }
        assertEquals(names.toSet().size, names.size) // no duplicates
    }

    @Test
    fun `HealthDataType JSON uses enum name`() {
        val jsonString = json.encodeToString(HealthDataType.serializer(), HealthDataType.HEART_RATE)
        assertEquals("\"HEART_RATE\"", jsonString.trim())
    }

    // =============================================
    // ExportSummary construction
    // =============================================

    @Test
    fun `ExportSummary default values are zero`() {
        val summary = ExportSummary()

        assertEquals(0L, summary.totalSteps)
        assertEquals(0.0, summary.avgHeartRate, 0.001)
        assertEquals(0.0, summary.totalCalories, 0.001)
        assertEquals(0.0, summary.totalDistanceMeters, 0.001)
        assertEquals(0L, summary.avgSleepMinutes)
        assertEquals(0.0, summary.totalActiveCalories, 0.001)
        assertEquals(0, summary.daysCount)
        assertEquals("", summary.startDate)
        assertEquals("", summary.endDate)
    }

    @Test
    fun `ExportSummary with full data`() {
        val summary = ExportSummary(
            totalSteps = 75000,
            avgHeartRate = 72.5,
            totalCalories = 12000.0,
            totalDistanceMeters = 50000.0,
            avgSleepMinutes = 420,
            totalActiveCalories = 2500.0,
            daysCount = 7,
            startDate = "2026-05-25",
            endDate = "2026-05-31"
        )

        assertEquals(75000L, summary.totalSteps)
        assertEquals(72.5, summary.avgHeartRate, 0.001)
        assertEquals(12000.0, summary.totalCalories, 0.001)
        assertEquals(50000.0, summary.totalDistanceMeters, 0.001)
        assertEquals(420L, summary.avgSleepMinutes)
        assertEquals(2500.0, summary.totalActiveCalories, 0.001)
        assertEquals(7, summary.daysCount)
        assertEquals("2026-05-25", summary.startDate)
        assertEquals("2026-05-31", summary.endDate)
    }

    @Test
    fun `ExportSummary with edge case values`() {
        val summary = ExportSummary(
            totalSteps = Long.MAX_VALUE,
            avgHeartRate = 220.0,  // max possible heart rate
            totalCalories = 0.0,
            totalDistanceMeters = -1.0,  // shouldn't happen, but test edge
            avgSleepMinutes = 0,
            totalActiveCalories = 999999.99,
            daysCount = 365,
            startDate = "",
            endDate = "2026-12-31"
        )

        assertEquals(Long.MAX_VALUE, summary.totalSteps)
        assertEquals(220.0, summary.avgHeartRate, 0.001)
        assertEquals(0.0, summary.totalCalories, 0.001)
        assertEquals(999999.99, summary.totalActiveCalories, 0.001)
        assertEquals(365, summary.daysCount)
    }

    // =============================================
    // Helper functions
    // =============================================

    @Test
    fun `sourceDisplayName returns known name for known package`() {
        assertEquals("Xiaomi Mi Fitness", sourceDisplayName("com.mi.health"))
        assertEquals("Google Fit", sourceDisplayName("com.google.android.apps.fitness"))
        assertEquals("Samsung Health", sourceDisplayName("com.samsung.samsunghealth"))
        assertEquals("Fitbit", sourceDisplayName("com.fitbit.FitbitMobile"))
    }

    @Test
    fun `sourceDisplayName returns package name for unknown package`() {
        val unknownPackage = "com.unknown.brand"
        assertEquals(unknownPackage, sourceDisplayName(unknownPackage))
    }

    @Test
    fun `sourceDisplayName is case sensitive`() {
        // Different case should not match
        val wrongCase = "COM.MI.HEALTH"
        assertEquals(wrongCase, sourceDisplayName(wrongCase))
    }

    @Test
    fun `Instant toDateString returns ISO local date`() {
        val instant = Instant.parse("2026-06-01T10:30:00Z")
        val dateStr = instant.toDateString()
        val expected = instant.atZone(ZoneId.systemDefault()).toLocalDate().toString()
        assertEquals(expected, dateStr)
    }

    @Test
    fun `Instant toDateTimeString returns ISO local datetime`() {
        val instant = Instant.parse("2026-06-01T10:30:45Z")
        val datetimeStr = instant.toDateTimeString()
        val expected = instant.atZone(ZoneId.systemDefault()).toLocalDateTime().toString()
        assertEquals(expected, datetimeStr)
    }

    @Test
    fun `KNOWN_SOURCE_PACKAGES contains all major brands`() {
        assertTrue(KNOWN_SOURCE_PACKAGES.containsKey("com.mi.health"))
        assertTrue(KNOWN_SOURCE_PACKAGES.containsKey("com.google.android.apps.fitness"))
        assertTrue(KNOWN_SOURCE_PACKAGES.containsKey("com.samsung.android.wearable.health"))
        assertTrue(KNOWN_SOURCE_PACKAGES.containsKey("com.fitbit.FitbitMobile"))
        assertTrue(KNOWN_SOURCE_PACKAGES.containsKey("com.huawei.health"))
        assertTrue(KNOWN_SOURCE_PACKAGES.containsKey("com.xiaomi.hm.health"))
    }

    // =============================================
    // StepsData edge cases
    // =============================================

    @Test
    fun `StepsData with zero steps`() {
        val data = StepsData(totalSteps = 0, recordsCount = 0)

        val jsonString = json.encodeToString(StepsData.serializer(), data)
        val restored = json.decodeFromString<StepsData>(jsonString)

        assertEquals(0, restored.totalSteps)
        assertEquals(0, restored.recordsCount)
    }

    @Test
    fun `StepsData with large values`() {
        val data = StepsData(totalSteps = 999_999_999, recordsCount = 10_000)

        val jsonString = json.encodeToString(StepsData.serializer(), data)
        val restored = json.decodeFromString<StepsData>(jsonString)

        assertEquals(999_999_999, restored.totalSteps)
        assertEquals(10_000, restored.recordsCount)
    }

    // =============================================
    // HeartRateData edge cases
    // =============================================

    @Test
    fun `HeartRateData with same min and max`() {
        val data = HeartRateData(avgBpm = 70.0, minBpm = 70, maxBpm = 70, recordsCount = 1)

        val jsonString = json.encodeToString(HeartRateData.serializer(), data)
        val restored = json.decodeFromString<HeartRateData>(jsonString)

        assertEquals(70.0, restored.avgBpm, 0.001)
        assertEquals(70, restored.minBpm)
        assertEquals(70, restored.maxBpm)
    }

    // =============================================
    // SleepData edge cases
    // =============================================

    @Test
    fun `SleepData with empty stages`() {
        val data = SleepData(totalDurationMinutes = 0, sleepStages = emptyMap(), recordsCount = 0)

        val jsonString = json.encodeToString(SleepData.serializer(), data)
        val restored = json.decodeFromString<SleepData>(jsonString)

        assertEquals(0, restored.totalDurationMinutes)
        assertTrue(restored.sleepStages.isEmpty())
    }

    // =============================================
    // SpeedData serialization
    // =============================================

    @Test
    fun `SpeedData full roundtrip`() {
        val data = SpeedData(avgSpeedMetersPerSecond = 1.5, recordsCount = 420)

        val jsonString = json.encodeToString(SpeedData.serializer(), data)
        val restored = json.decodeFromString<SpeedData>(jsonString)

        assertNotNull(restored.avgSpeedMetersPerSecond)
        assertEquals(1.5, restored.avgSpeedMetersPerSecond!!, 0.001)
        assertEquals(420, restored.recordsCount)
    }

    @Test
    fun `SpeedData with null avgSpeed`() {
        val data = SpeedData(avgSpeedMetersPerSecond = null, recordsCount = 10)

        val jsonString = json.encodeToString(SpeedData.serializer(), data)
        val restored = json.decodeFromString<SpeedData>(jsonString)

        assertNull(restored.avgSpeedMetersPerSecond)
        assertEquals(10, restored.recordsCount)
    }

    @Test
    fun `SpeedData with default recordsCount`() {
        val data = SpeedData(avgSpeedMetersPerSecond = 2.0)

        val jsonString = json.encodeToString(SpeedData.serializer(), data)
        val restored = json.decodeFromString<SpeedData>(jsonString)

        assertNotNull(restored.avgSpeedMetersPerSecond)
        assertEquals(2.0, restored.avgSpeedMetersPerSecond!!, 0.001)
        assertEquals(0, restored.recordsCount)
    }

    @Test
    fun `SpeedData with all null defaults`() {
        val data = SpeedData()

        val jsonString = json.encodeToString(SpeedData.serializer(), data)
        val restored = json.decodeFromString<SpeedData>(jsonString)

        assertNull(restored.avgSpeedMetersPerSecond)
        assertEquals(0, restored.recordsCount)
    }

    @Test
    fun `SpeedData JSON uses SerialName annotations`() {
        val data = SpeedData(avgSpeedMetersPerSecond = 1.5, recordsCount = 200)

        val jsonString = json.encodeToString(SpeedData.serializer(), data)
        val parsed = kotlinx.serialization.json.Json.parseToJsonElement(jsonString).jsonObject

        assertTrue(parsed.containsKey("avg_speed_meters_per_second"))
        assertFalse(parsed.containsKey("avgSpeedMetersPerSecond"))
        assertTrue(parsed.containsKey("records_count"))
        assertFalse(parsed.containsKey("recordsCount"))
    }

    // =============================================
    // ExportMetadata edge cases
    // =============================================

    @Test
    fun `ExportMetadata without sourceDevice`() {
        val metadata = ExportMetadata("1.0.0", "2026-06-01T12:00:00", "UTC")

        val jsonString = json.encodeToString(ExportMetadata.serializer(), metadata)
        val restored = json.decodeFromString<ExportMetadata>(jsonString)

        assertEquals("1.0.0", restored.appVersion)
        assertEquals("UTC", restored.timezone)
        assertNull(restored.sourceDevice)
    }
}
