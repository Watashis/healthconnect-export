package com.healthconnect.export.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.healthconnect.export.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class HealthConnectRepository(private val context: Context) {

    companion object {
        private const val TAG = "HealthConnectRepo"
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val PAGE_SIZE = 5000

        /**
         * Пакеты приложений для фильтрации dataOrigin, упорядоченные по приоритету.
         * Если данные есть от нескольких источников, берётся первый из списка.
         */
        val PREFERRED_PACKAGES = listOf(
            "com.mi.health",              // Xiaomi Mi Fitness
            "com.xiaomi.hm.health",       // Xiaomi Wear / Mi Band
            "com.google.android.apps.fitness", // Google Fit
            "com.samsung.android.wearable.health", // Samsung Health
            "com.fitbit.FitbitMobile",    // Fitbit
            "com.mobvoi.companion.at",    // Mobvoi / TicWatch
            "com.huawei.health",          // Huawei Health
            "com.hmdm.wearable.health",   // Nokia Health
            "com.sec.android.app.shealth", // Samsung S Health
            "com.htc.fitness",            // HTC
            "com.sonymobile.advancedwidget.health" // Sony
        )
    }

    private var client: HealthConnectClient? = null
        get() {
            if (field == null) {
                try {
                    val status = HealthConnectClient.getSdkStatus(context)
                    if (status == HealthConnectClient.SDK_AVAILABLE) {
                        field = HealthConnectClient.getOrCreate(context)
                    }
                } catch (_: Exception) {
                    field = null
                }
            }
            return field
        }

    suspend fun isHealthConnectAvailable(): Boolean {
        return client != null
    }

    /**
     * Проверяет, установлено ли приложение Health Connect на устройстве
     */
    fun isHealthConnectInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(HEALTH_CONNECT_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Создаёт Intent для открытия экрана разрешений Health Connect
     * через ACTION_REQUEST_PERMISSIONS с разрешениями в extra
     */
    fun createHealthPermissionsIntent(permissions: Set<String>): Intent {
        return Intent("androidx.health.ACTION_REQUEST_PERMISSIONS").apply {
            `package` = HEALTH_CONNECT_PACKAGE
            putStringArrayListExtra(
                "androidx.health.extra.PERMISSION_NAMES",
                ArrayList(permissions)
            )
        }
    }

    /**
     * Возвращает ActivityResultContract для запроса разрешений Health Connect
     */
    fun createPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract(context.packageName)
    }

    /**
     * Возвращает набор Health Connect разрешений для указанных типов данных
     */
    fun getPermissionsForTypes(types: Set<HealthDataType>): Set<String> {
        val permissions = mutableSetOf<String>()
        types.forEach { type ->
            when (type) {
                HealthDataType.STEPS -> permissions.add(HealthPermission.getReadPermission(StepsRecord::class))
                HealthDataType.HEART_RATE -> permissions.add(HealthPermission.getReadPermission(HeartRateRecord::class))
                HealthDataType.SLEEP -> permissions.add(HealthPermission.getReadPermission(SleepSessionRecord::class))
                HealthDataType.CALORIES -> permissions.add(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
                HealthDataType.DISTANCE -> permissions.add(HealthPermission.getReadPermission(DistanceRecord::class))
                HealthDataType.FLOORS_CLIMBED -> permissions.add(HealthPermission.getReadPermission(FloorsClimbedRecord::class))
                HealthDataType.ACTIVE_CALORIES -> permissions.add(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
                HealthDataType.WEIGHT -> permissions.add(HealthPermission.getReadPermission(WeightRecord::class))
                HealthDataType.BODY_FAT -> permissions.add(HealthPermission.getReadPermission(BodyFatRecord::class))
                HealthDataType.BLOOD_PRESSURE -> permissions.add(HealthPermission.getReadPermission(BloodPressureRecord::class))
                HealthDataType.BLOOD_GLUCOSE -> permissions.add(HealthPermission.getReadPermission(BloodGlucoseRecord::class))
                HealthDataType.OXYGEN_SATURATION -> permissions.add(HealthPermission.getReadPermission(OxygenSaturationRecord::class))
                HealthDataType.BODY_TEMPERATURE -> permissions.add(HealthPermission.getReadPermission(BodyTemperatureRecord::class))
                HealthDataType.RESPIRATORY_RATE -> permissions.add(HealthPermission.getReadPermission(RespiratoryRateRecord::class))
                HealthDataType.HYDRATION -> permissions.add(HealthPermission.getReadPermission(HydrationRecord::class))
                HealthDataType.RESTING_HEART_RATE -> permissions.add(HealthPermission.getReadPermission(RestingHeartRateRecord::class))
                HealthDataType.EXERCISE -> permissions.add(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
                HealthDataType.NUTRITION -> permissions.add(HealthPermission.getReadPermission(NutritionRecord::class))
                HealthDataType.MENSTRUATION -> permissions.add(HealthPermission.getReadPermission(MenstruationFlowRecord::class))
                HealthDataType.SPEED -> permissions.add(HealthPermission.getReadPermission(SpeedRecord::class))
            }
        }
        return permissions
    }

    /**
     * Проверяет, какие разрешения уже предоставлены
     */
    suspend fun getGrantedPermissions(): Set<String> {
        val c = client ?: return emptySet()
        return c.permissionController.getGrantedPermissions()
    }

    /**
     * Проверяет, предоставлены ли все необходимые разрешения для указанных типов
     */
    suspend fun checkPermissions(types: Set<HealthDataType>): Boolean {
        val c = client ?: return false
        val required = getPermissionsForTypes(types)
        val granted = c.permissionController.getGrantedPermissions()
        return granted.containsAll(required)
    }

    /**
     * Discovers available data source packages by querying recent health records.
     * Returns a set of package names that have data available on device.
     */
    suspend fun getAvailableSources(): Set<String> = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext emptySet()
        val now = Instant.now()
        val weekAgo = now.minus(7, ChronoUnit.DAYS)
        val timeFilter = TimeRangeFilter.between(weekAgo, now)

        val origins = mutableSetOf<String>()

        // Query steps as a representative data type to discover origins
        try {
            val stepsRequest = ReadRecordsRequest(
                StepsRecord::class,
                timeRangeFilter = timeFilter,
                pageSize = 1
            )
            val stepsResponse = c.readRecords(stepsRequest)
            stepsResponse.records.forEach { record ->
                record.metadata.dataOrigin?.packageName?.let { origins.add(it) }
            }
        } catch (_: Exception) {}

        // Also query heart rate for more complete origin discovery
        try {
            val hrRequest = ReadRecordsRequest(
                HeartRateRecord::class,
                timeRangeFilter = timeFilter,
                pageSize = 1
            )
            val hrResponse = c.readRecords(hrRequest)
            hrResponse.records.forEach { record ->
                record.metadata.dataOrigin?.packageName?.let { origins.add(it) }
            }
        } catch (_: Exception) {}

        Log.d(TAG, "getAvailableSources: $origins")
        origins
    }

    /**
     * Reads all specified health data types for a single day
     */
    suspend fun readDay(
        date: LocalDate,
        types: Set<HealthDataType>,
        selectedSourcePackage: String? = null
    ): DailyHealthRecord = withContext(Dispatchers.IO) {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        val timeFilter = TimeRangeFilter.between(start, end)

        val metadata = ExportMetadata(
            appVersion = "1.0.0",
            exportTimestamp = Instant.now().toDateTimeString(),
            timezone = ZoneId.systemDefault().id,
            sourceDevice = android.os.Build.MODEL
        )

        DailyHealthRecord(
            date = date.toString(),
            steps = if (types.contains(HealthDataType.STEPS)) readSteps(timeFilter, selectedSourcePackage) else null,
            heartRate = if (types.contains(HealthDataType.HEART_RATE)) readHeartRate(timeFilter) else null,
            sleep = if (types.contains(HealthDataType.SLEEP)) readSleep(timeFilter) else null,
            calories = if (types.contains(HealthDataType.CALORIES)) readCalories(timeFilter, selectedSourcePackage) else null,
            distance = if (types.contains(HealthDataType.DISTANCE)) readDistance(timeFilter, selectedSourcePackage) else null,
            floorsClimbed = if (types.contains(HealthDataType.FLOORS_CLIMBED)) readFloorsClimbed(timeFilter, selectedSourcePackage) else null,
            activeCalories = if (types.contains(HealthDataType.ACTIVE_CALORIES)) readActiveCalories(timeFilter, selectedSourcePackage) else null,
            weight = if (types.contains(HealthDataType.WEIGHT)) readWeight(timeFilter) else null,
            bodyFat = if (types.contains(HealthDataType.BODY_FAT)) readBodyFat(timeFilter) else null,
            bloodPressure = if (types.contains(HealthDataType.BLOOD_PRESSURE)) readBloodPressure(timeFilter) else null,
            bloodGlucose = if (types.contains(HealthDataType.BLOOD_GLUCOSE)) readBloodGlucose(timeFilter) else null,
            oxygenSaturation = if (types.contains(HealthDataType.OXYGEN_SATURATION)) readOxygenSaturation(timeFilter) else null,
            bodyTemperature = if (types.contains(HealthDataType.BODY_TEMPERATURE)) readBodyTemperature(timeFilter) else null,
            respiratoryRate = if (types.contains(HealthDataType.RESPIRATORY_RATE)) readRespiratoryRate(timeFilter) else null,
            hydration = if (types.contains(HealthDataType.HYDRATION)) readHydration(timeFilter) else null,
            restingHeartRate = if (types.contains(HealthDataType.RESTING_HEART_RATE)) readRestingHeartRate(timeFilter) else null,
            exercises = if (types.contains(HealthDataType.EXERCISE)) readExercises(timeFilter) else null,
            nutrition = if (types.contains(HealthDataType.NUTRITION)) readNutrition(timeFilter) else null,
            speed = if (types.contains(HealthDataType.SPEED)) readSpeed(timeFilter) else null,
            menstruation = if (types.contains(HealthDataType.MENSTRUATION)) readMenstruation(timeFilter) else null,
            metadata = metadata
        )
    }

    /**
     * Reads multiple days of data
     */
    suspend fun readPeriod(
        startDate: LocalDate,
        endDate: LocalDate,
        types: Set<HealthDataType>,
        selectedSourcePackage: String? = null
    ): List<DailyHealthRecord> {
        val records = mutableListOf<DailyHealthRecord>()
        var current = startDate
        while (!current.isAfter(endDate)) {
            records.add(readDay(current, types, selectedSourcePackage))
            current = current.plusDays(1)
        }
        return records
    }

    // ===== Pagination helper =====

    /**
     * Вычитывает ВСЕ записи для указанного типа, используя пагинацию.
     * Стандартный readRecords без pageToken возвращает до 10 000 записей,
     * а на некоторых прошивках (MIUI/HyperOS) может быть меньше (напр. 1000).
     * Чтобы гарантированно получить всё, используем pageToken из ответа.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal suspend fun <T : Record> readAllPages(request: ReadRecordsRequest<T>): List<T> {
        val c = client ?: return emptyList()
        val allRecords = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val recordType = request.recordType
            val pageRequest = ReadRecordsRequest(
                recordType = recordType,
                timeRangeFilter = request.timeRangeFilter,
                dataOriginFilter = request.dataOriginFilter,
                ascendingOrder = request.ascendingOrder,
                pageSize = PAGE_SIZE,
                pageToken = pageToken
            )
            val response = c.readRecords(pageRequest)
            allRecords.addAll(response.records)
            pageToken = response.pageToken
            Log.d(TAG, "readAllPages: got ${allRecords.size} records, pageToken=$pageToken")
        } while (pageToken != null)
        return allRecords
    }

    // ===== Private readers =====

    private suspend fun readSteps(filter: TimeRangeFilter, selectedSourcePackage: String? = null): StepsData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(StepsRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null

        val filtered = filterByPreferredOrigin(allRecords, selectedSourcePackage)
        if (filtered.isEmpty()) return null

        // Single source — just sum (overlaps from same source are rare in practice)
        val total = filtered.sumOf { it.count }

        Log.d(TAG, "readSteps: raw=${allRecords.size}, filtered=${filtered.size}, totalSteps=$total")
        return StepsData(totalSteps = total, recordsCount = filtered.size)
    }

    /**
     * Фильтрует записи по предпочитаемому пакету-источнику данных.
     * Необходимо, чтобы одни и те же данные не суммировались от разных источников одновременно.
     *
     * @param selectedSourcePackage если задан — используется только этот источник
     * @param records записи для фильтрации
     * @return отфильтрованные записи от одного источника
     */
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    internal fun <T> filterByPreferredOrigin(records: List<T>, selectedSourcePackage: String? = null): List<T> {
        if (records.isEmpty()) return records

        val groupedByOrigin = records.groupBy { record ->
            val origin = (record as? androidx.health.connect.client.records.Record)?.metadata?.dataOrigin
            origin?.packageName ?: "unknown"
        }

        Log.d(TAG, "filterByPreferredOrigin: sources=${groupedByOrigin.keys}, selected=$selectedSourcePackage")

        // 0. If user explicitly selected a source, use it (even if not in preferred list)
        if (selectedSourcePackage != null) {
            val selected = groupedByOrigin[selectedSourcePackage]
            if (selected != null) {
                Log.d(TAG, "filterByPreferredOrigin: using user-selected '$selectedSourcePackage' (${selected.size} records)")
                return selected
            }
            Log.w(TAG, "filterByPreferredOrigin: user-selected '$selectedSourcePackage' not found in sources, falling back to auto")
        }

        // 1. Try preferred packages in order
        for (preferred in PREFERRED_PACKAGES) {
            if (groupedByOrigin.containsKey(preferred)) {
                val selected = groupedByOrigin[preferred]!!
                Log.d(TAG, "filterByPreferredOrigin: using '$preferred' (${selected.size} records), ignoring ${groupedByOrigin.keys - preferred}")
                return selected
            }
        }

        // 2. Fallback: pick the source with the most records (never mix sources)
        val bestSource = groupedByOrigin.maxByOrNull { (_, records) -> records.size }
        if (bestSource != null) {
            val (source, selected) = bestSource
            Log.d(TAG, "filterByPreferredOrigin: no preferred, using '$source' with most records (${selected.size}), ignoring ${groupedByOrigin.keys - source}")
            return selected
        }

        return records
    }

    private suspend fun readHeartRate(filter: TimeRangeFilter): HeartRateData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val samples = allRecords.flatMap { it.samples }
        if (samples.isEmpty()) return null
        val beats = samples.map { it.beatsPerMinute }
        return HeartRateData(
            avgBpm = beats.average(),
            minBpm = beats.min().toInt(),
            maxBpm = beats.max().toInt(),
            recordsCount = allRecords.size
        )
    }

    private suspend fun readSleep(filter: TimeRangeFilter): SleepData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val totalMinutes = allRecords.sumOf {
            ChronoUnit.MINUTES.between(it.startTime, it.endTime)
        }
        val stageDurations = mutableMapOf<String, Long>()
        allRecords.forEach { record ->
            record.stages.forEach { stage ->
                val name = sleepStageToString(stage.stage) ?: "Неизвестно"
                val duration = ChronoUnit.MINUTES.between(stage.startTime, stage.endTime)
                if (duration > 0) {
                    stageDurations[name] = (stageDurations[name] ?: 0) + duration
                }
            }
        }
        return SleepData(
            totalDurationMinutes = totalMinutes,
            sleepStages = stageDurations,
            recordsCount = allRecords.size
        )
    }

    private suspend fun readCalories(filter: TimeRangeFilter, selectedSourcePackage: String? = null): CaloriesData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val filtered = filterByPreferredOrigin(allRecords, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.energy.inKilocalories }
        return CaloriesData(totalCalories = total, recordsCount = filtered.size)
    }

    private suspend fun readDistance(filter: TimeRangeFilter, selectedSourcePackage: String? = null): DistanceData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(DistanceRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val filtered = filterByPreferredOrigin(allRecords, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.distance.inMeters }
        return DistanceData(totalDistanceMeters = total, recordsCount = filtered.size)
    }

    private suspend fun readFloorsClimbed(filter: TimeRangeFilter, selectedSourcePackage: String? = null): FloorsClimbedData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(FloorsClimbedRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val filtered = filterByPreferredOrigin(allRecords, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.floors }
        return FloorsClimbedData(totalFloors = total, recordsCount = filtered.size)
    }

    private suspend fun readActiveCalories(filter: TimeRangeFilter, selectedSourcePackage: String? = null): ActiveCaloriesData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(ActiveCaloriesBurnedRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val filtered = filterByPreferredOrigin(allRecords, selectedSourcePackage)
        if (filtered.isEmpty()) return null
        val total = filtered.sumOf { it.energy.inKilocalories }
        return ActiveCaloriesData(totalCalories = total, recordsCount = filtered.size)
    }

    private suspend fun readWeight(filter: TimeRangeFilter): WeightData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(WeightRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        // WeightRecord stores weight in kilograms
        val avg = allRecords.map { it.weight.inKilograms }.average()
        return WeightData(weightKg = avg, recordsCount = allRecords.size)
    }

    private suspend fun readBodyFat(filter: TimeRangeFilter): BodyFatData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(BodyFatRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val avg = allRecords.map { it.percentage.value }.average()
        return BodyFatData(percentage = avg, recordsCount = allRecords.size)
    }

    private suspend fun readBloodPressure(filter: TimeRangeFilter): BloodPressureData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(BloodPressureRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val systolicAvg = allRecords.map { it.systolic.inMillimetersOfMercury }.average()
        val diastolicAvg = allRecords.map { it.diastolic.inMillimetersOfMercury }.average()
        val position = bodyPositionToString(allRecords.firstOrNull()?.bodyPosition)
        return BloodPressureData(
            systolicMmHg = systolicAvg,
            diastolicMmHg = diastolicAvg,
            bodyPosition = position,
            recordsCount = allRecords.size
        )
    }

    private suspend fun readBloodGlucose(filter: TimeRangeFilter): BloodGlucoseData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(BloodGlucoseRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val avg = allRecords.map { it.level.inMillimolesPerLiter }.average()
        val source = specimenSourceToString(allRecords.firstOrNull()?.specimenSource)
        val mealType = mealTypeToString(allRecords.firstOrNull()?.mealType)
        return BloodGlucoseData(
            level = avg,
            specimenSource = source,
            mealType = mealType,
            recordsCount = allRecords.size
        )
    }

    private suspend fun readOxygenSaturation(filter: TimeRangeFilter): OxygenSaturationData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val avg = allRecords.map { it.percentage.value }.average()
        return OxygenSaturationData(percentage = avg, recordsCount = allRecords.size)
    }

    private suspend fun readBodyTemperature(filter: TimeRangeFilter): BodyTemperatureData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(BodyTemperatureRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val avg = allRecords.map { it.temperature.inCelsius }.average()
        val location = measurementLocationToString(allRecords.firstOrNull()?.measurementLocation)
        return BodyTemperatureData(
            temperatureCelsius = avg,
            measurementLocation = location,
            recordsCount = allRecords.size
        )
    }

    private suspend fun readRespiratoryRate(filter: TimeRangeFilter): RespiratoryRateData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(RespiratoryRateRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val avg = allRecords.map { it.rate }.average()
        return RespiratoryRateData(rate = avg, recordsCount = allRecords.size)
    }

    private suspend fun readHydration(filter: TimeRangeFilter): HydrationData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(HydrationRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val total = allRecords.sumOf { it.volume.inLiters }
        return HydrationData(totalVolumeLiters = total, recordsCount = allRecords.size)
    }

    private suspend fun readRestingHeartRate(filter: TimeRangeFilter): RestingHeartRateData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(RestingHeartRateRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val bpmValues = allRecords.map { it.beatsPerMinute }
        return RestingHeartRateData(
            avgBpm = bpmValues.average(),
            minBpm = bpmValues.min(),
            maxBpm = bpmValues.max(),
            recordsCount = allRecords.size
        )
    }

    private suspend fun readExercises(filter: TimeRangeFilter): List<ExerciseData>? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        return allRecords.map { record ->
            ExerciseData(
                exerciseType = exerciseTypeToString(record.exerciseType) ?: "Неизвестно",
                startTime = record.startTime.toDateTimeString(),
                endTime = record.endTime.toDateTimeString(),
                durationMinutes = ChronoUnit.MINUTES.between(record.startTime, record.endTime),
                title = record.title,
                notes = record.notes
            )
        }
    }

    private suspend fun readNutrition(filter: TimeRangeFilter): List<NutritionData>? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(NutritionRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        return allRecords.map { record ->
            NutritionData(
                name = record.name,
                mealType = nutritionMealTypeToString(record.mealType),
                energyKcal = record.energy?.inKilocalories,
                proteinG = record.protein?.inGrams,
                totalCarbohydrateG = record.totalCarbohydrate?.inGrams,
                fatG = record.totalFat?.inGrams,
                dietaryFiberG = record.dietaryFiber?.inGrams,
                sugarG = record.sugar?.inGrams,
                saturatedFatG = record.saturatedFat?.inGrams,
                transFatG = record.transFat?.inGrams,
                cholesterolMg = record.cholesterol?.inMilligrams,
                sodiumMg = record.sodium?.inMilligrams,
                potassiumMg = record.potassium?.inMilligrams,
                caffeineMg = record.caffeine?.inMilligrams,
                calciumMg = record.calcium?.inMilligrams,
                ironMg = record.iron?.inMilligrams,
                magnesiumMg = record.magnesium?.inMilligrams,
                vitaminCMg = record.vitaminC?.inMilligrams,
                vitaminDMcg = record.vitaminD?.inMicrograms
            )
        }
    }

    private suspend fun readSpeed(filter: TimeRangeFilter): SpeedData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(SpeedRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val allSamples = allRecords.flatMap { it.samples }
        if (allSamples.isEmpty()) return null
        val avgSpeed = allSamples.map { it.speed.inMetersPerSecond }.average()
        return SpeedData(
            avgSpeedMetersPerSecond = avgSpeed,
            recordsCount = allRecords.size
        )
    }

    private suspend fun readMenstruation(filter: TimeRangeFilter): MenstruationData? {
        val c = client ?: return null
        val allRecords = readAllPages(ReadRecordsRequest(MenstruationFlowRecord::class, timeRangeFilter = filter))
        if (allRecords.isEmpty()) return null
        val record = allRecords.first()
        return MenstruationData(
            flowType = menstruationFlowToString(record.flow),
            time = record.time.toDateTimeString()
        )
    }
}
