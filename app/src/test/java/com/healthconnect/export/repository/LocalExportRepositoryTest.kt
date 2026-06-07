package com.healthconnect.export.repository

import android.content.Context
import com.healthconnect.export.data.DailyHealthRecord
import com.healthconnect.export.data.ExportConfig
import com.healthconnect.export.data.ExportFrequency
import com.healthconnect.export.data.ExportMetadata
import com.healthconnect.export.data.HealthDataType
import com.healthconnect.export.data.StepsData
import kotlinx.coroutines.runBlocking
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
import java.time.LocalDate
import kotlin.io.path.createTempDirectory

@RunWith(MockitoJUnitRunner.Silent::class)
class LocalExportRepositoryTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var repo: LocalExportRepository
    private lateinit var tempDir: File
    private lateinit var filesDir: File
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val testConfig = ExportConfig(
        enabledTypes = setOf(HealthDataType.STEPS),
        frequency = ExportFrequency.DAILY,
        autoSyncDrive = false,
        outputDirectory = "HealthConnectExport"
    )

    @Before
    fun setup() {
        tempDir = createTempDirectory("hc-export-test-").toFile()
        filesDir = createTempDirectory("hc-export-fallback-").toFile()

        whenever(mockContext.getExternalFilesDirs(anyOrNull())).thenReturn(arrayOf(tempDir))
        whenever(mockContext.filesDir).thenReturn(filesDir)

        repo = LocalExportRepository(mockContext)
    }

    @After
    fun cleanup() {
        tempDir.deleteRecursively()
        filesDir.deleteRecursively()
    }

    // ============================
    // getExportDirectory
    // ============================

    @Test
    fun `getExportDirectory creates export dir and returns it`() {
        val dir = repo.getExportDirectory(testConfig)
        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertEquals("HealthConnectExport", dir.name)
    }

    @Test
    fun `getExportDirectory falls back to filesDir when external dirs unavailable`() {
        whenever(mockContext.getExternalFilesDirs(anyOrNull())).thenReturn(emptyArray())

        val dir = repo.getExportDirectory(testConfig)
        assertTrue(dir.exists())
        assertTrue(dir.absolutePath.contains(filesDir.absolutePath))
    }

    @Test
    fun `getExportDirectory reuses existing directory`() {
        val first = repo.getExportDirectory(testConfig)
        val second = repo.getExportDirectory(testConfig)
        assertEquals(first, second)
    }

    // ============================
    // getFilenameForDate
    // ============================

    @Test
    fun `getFilenameForDate formats correctly`() {
        val date = LocalDate.of(2026, 5, 24)
        assertEquals("health_2026-05-24.json", repo.getFilenameForDate(date))
    }

    @Test
    fun `getFilenameForDate pads month and day with zeroes`() {
        val date = LocalDate.of(2026, 1, 3)
        assertEquals("health_2026-01-03.json", repo.getFilenameForDate(date))
    }

    // ============================
    // isExported
    // ============================

    @Test
    fun `isExported returns false when file does not exist`() {
        val result = repo.isExported(LocalDate.of(2026, 5, 24), testConfig)
        assertFalse(result)
    }

    @Test
    fun `isExported returns true when file exists and has content`() {
        val date = LocalDate.of(2026, 5, 24)
        val file = File(repo.getExportDirectory(testConfig), repo.getFilenameForDate(date))
        file.writeText("{}")

        val result = repo.isExported(date, testConfig)
        assertTrue(result)
    }

    @Test
    fun `isExported returns false when file exists but empty`() {
        val date = LocalDate.of(2026, 5, 24)
        val file = File(repo.getExportDirectory(testConfig), repo.getFilenameForDate(date))
        file.createNewFile()

        val result = repo.isExported(date, testConfig)
        assertFalse(result)
    }

    // ============================
    // saveDailyRecord
    // ============================

    @Test
    fun `saveDailyRecord writes valid JSON file`() = runBlocking {
        val record = DailyHealthRecord(
            date = "2026-05-24",
            steps = StepsData(totalSteps = 12345, recordsCount = 480),
            metadata = ExportMetadata("1.0", "2026-05-24T12:00:00", "Europe/Moscow", "test")
        )

        val file = repo.saveDailyRecord(record, testConfig)

        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        val content = file.readText()
        assertTrue(content.contains("12345"))
        assertTrue(content.contains("2026-05-24"))
    }

    @Test
    fun `saveDailyRecord file path matches formatted date`() = runBlocking {
        val record = DailyHealthRecord(
            date = "2026-05-24",
            metadata = ExportMetadata("1.0", "2026-05-24T12:00:00", "Europe/Moscow", "test")
        )

        val file = repo.saveDailyRecord(record, testConfig)

        assertEquals("health_2026-05-24.json", file.name)
    }

    @Test
    fun `saveDailyRecord writes parseable JSON`() = runBlocking {
        val record = DailyHealthRecord(
            date = "2026-05-24",
            steps = StepsData(totalSteps = 100, recordsCount = 1),
            metadata = ExportMetadata("1.0", "2026-05-24T12:00:00", "Europe/Moscow", "test")
        )

        val file = repo.saveDailyRecord(record, testConfig)
        val parsed = json.decodeFromString<DailyHealthRecord>(file.readText())
        assertEquals("2026-05-24", parsed.date)
        assertEquals(100L, parsed.steps?.totalSteps)
    }

    // ============================
    // saveRecords
    // ============================

    @Test
    fun `saveRecords saves multiple records`() = runBlocking {
        val records = listOf(
            DailyHealthRecord("2026-05-24", metadata = ExportMetadata("1.0", "", "UTC", "")),
            DailyHealthRecord("2026-05-25", metadata = ExportMetadata("1.0", "", "UTC", "")),
            DailyHealthRecord("2026-05-26", metadata = ExportMetadata("1.0", "", "UTC", ""))
        )

        val files = repo.saveRecords(records, testConfig)

        assertEquals(3, files.size)
        files.forEach { assertTrue(it.exists()) }
        assertEquals(listOf("health_2026-05-24.json", "health_2026-05-25.json", "health_2026-05-26.json"),
            files.map { it.name }.sorted())
    }

    @Test
    fun `saveRecords empty list returns empty list`() = runBlocking {
        val files = repo.saveRecords(emptyList(), testConfig)
        assertTrue(files.isEmpty())
    }

    // ============================
    // listExportedFiles
    // ============================

    @Test
    fun `listExportedFiles returns nothing when no files exist`() {
        val result = repo.listExportedFiles(testConfig)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listExportedFiles lists only JSON files`() {
        val dir = repo.getExportDirectory(testConfig)
        File(dir, "health_2026-05-24.json").writeText("{}")
        File(dir, "notes.txt").writeText("hello")
        File(dir, "data.csv").writeText("a,b,c")

        val result = repo.listExportedFiles(testConfig)

        assertEquals(1, result.size)
        assertEquals(LocalDate.of(2026, 5, 24), result[0].first)
        assertEquals("health_2026-05-24.json", result[0].second.name)
    }

    @Test
    fun `listExportedFiles skips files with invalid date names`() {
        val dir = repo.getExportDirectory(testConfig)
        File(dir, "health_2026-13-01.json").writeText("{}")  // invalid month
        File(dir, "readme.json").writeText("{}")               // no date prefix

        val result = repo.listExportedFiles(testConfig)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `listExportedFiles returns files sorted by date`() {
        val dir = repo.getExportDirectory(testConfig)
        File(dir, "health_2026-05-26.json").writeText("{}")
        File(dir, "health_2026-05-24.json").writeText("{}")
        File(dir, "health_2026-05-25.json").writeText("{}")

        val result = repo.listExportedFiles(testConfig)

        assertEquals(3, result.size)
        assertEquals(LocalDate.of(2026, 5, 24), result[0].first)
        assertEquals(LocalDate.of(2026, 5, 25), result[1].first)
        assertEquals(LocalDate.of(2026, 5, 26), result[2].first)
    }

    @Test
    fun `listExportedFiles multiple exports returns all`() {
        val dir = repo.getExportDirectory(testConfig)
        File(dir, "health_2026-05-24.json").writeText("{}")
        File(dir, "health_2026-05-25.json").writeText("{}")

        val result = repo.listExportedFiles(testConfig)

        assertEquals(2, result.size)
    }

    // ============================
    // cleanupOldExports
    // ============================

    @Test
    fun `cleanupOldExports deletes files older than cutoff`() {
        val today = LocalDate.now()
        val dir = repo.getExportDirectory(testConfig)
        File(dir, "health_${today.minusDays(30)}.json").writeText("old")
        File(dir, "health_${today.minusDays(14)}.json").writeText("old")
        File(dir, "health_${today.minusDays(2)}.json").writeText("current")
        File(dir, "health_${today}.json").writeText("current")

        repo.cleanupOldExports(7, testConfig)

        val remaining = repo.listExportedFiles(testConfig)
        assertEquals(2, remaining.size)
        remaining.forEach { (date, _) ->
            assertTrue(date.isAfter(LocalDate.now().minusDays(7)))
        }
    }

    @Test
    fun `cleanupOldExports does nothing when all files are recent`() {
        val dir = repo.getExportDirectory(testConfig)
        val today = LocalDate.now()
        File(dir, repo.getFilenameForDate(today)).writeText("{}")
        File(dir, repo.getFilenameForDate(today.minusDays(1))).writeText("{}")

        repo.cleanupOldExports(30, testConfig)

        val result = repo.listExportedFiles(testConfig)
        assertEquals(2, result.size)
        assertTrue(result.any { it.first == today })
        assertTrue(result.any { it.first == today.minusDays(1) })
    }

    @Test
    fun `cleanupOldExports handles empty directory gracefully`() {
        // Should not throw
        repo.cleanupOldExports(7, testConfig)
        assertTrue(true)
    }

    @Test
    fun `cleanupOldExports with zero days deletes all`() {
        val today = LocalDate.now()
        val dir = repo.getExportDirectory(testConfig)
        File(dir, "health_${today.minusDays(1)}.json").writeText("{}")
        File(dir, "health_${today.minusDays(2)}.json").writeText("{}")

        repo.cleanupOldExports(0, testConfig)

        val result = repo.listExportedFiles(testConfig)
        assertTrue(result.isEmpty())
    }

    // ============================
    // deleteExport
    // ============================

    @Test
    fun `deleteExport deletes existing file and returns true`() {
        val date = LocalDate.of(2026, 5, 24)
        val dir = repo.getExportDirectory(testConfig)
        val file = File(dir, repo.getFilenameForDate(date))
        file.writeText("{}")
        assertTrue(file.exists())

        val result = repo.deleteExport(date, testConfig)

        assertTrue(result)
        assertFalse(file.exists())
    }

    @Test
    fun `deleteExport returns false when file does not exist`() {
        val result = repo.deleteExport(LocalDate.of(2026, 5, 24), testConfig)
        assertFalse(result)
    }
}
