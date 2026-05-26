package com.healthconnect.export.repository

import android.content.Context
import androidx.core.content.ContextCompat
import com.healthconnect.export.data.DailyHealthRecord
import com.healthconnect.export.data.ExportConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class LocalExportRepository(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * Gets the export directory. Tries external documents dir first, falls back to app files.
     */
    fun getExportDirectory(config: ExportConfig): File {
        val baseDir = ContextCompat.getExternalFilesDirs(context, null)
            .firstOrNull { it != null && (it.exists() || it.mkdirs()) }
            ?: context.filesDir
        val exportDir = File(baseDir, config.outputDirectory)
        if (!exportDir.exists()) exportDir.mkdirs()
        return exportDir
    }

    /**
     * Returns the expected filename for a given date
     */
    fun getFilenameForDate(date: LocalDate): String {
        return "health_${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}.json"
    }

    /**
     * Checks if a day's export already exists
     */
    fun isExported(date: LocalDate, config: ExportConfig): Boolean {
        val file = File(getExportDirectory(config), getFilenameForDate(date))
        return file.exists() && file.length() > 0
    }

    /**
     * Saves a single day's record as JSON file
     */
    suspend fun saveDailyRecord(
        record: DailyHealthRecord,
        config: ExportConfig
    ): File = withContext(Dispatchers.IO) {
        val dir = getExportDirectory(config)
        val date = LocalDate.parse(record.date)
        val file = File(dir, getFilenameForDate(date))
        file.writeText(json.encodeToString(record))
        file
    }

    /**
     * Saves multiple daily records
     */
    suspend fun saveRecords(
        records: List<DailyHealthRecord>,
        config: ExportConfig
    ): List<File> = withContext(Dispatchers.IO) {
        records.map { saveDailyRecord(it, config) }
    }

    /**
     * Lists all exported files with their dates
     */
    fun listExportedFiles(config: ExportConfig): List<Pair<LocalDate, File>> {
        val dir = getExportDirectory(config)
        return dir.listFiles { f -> f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                val dateStr = file.name.removePrefix("health_").removeSuffix(".json")
                try {
                    LocalDate.parse(dateStr) to file
                } catch (_: Exception) { null }
            }
            ?.sortedBy { it.first }
            ?: emptyList()
    }

    /**
     * Deletes exports older than the retention period (in days)
     */
    fun cleanupOldExports(daysToKeep: Int, config: ExportConfig) {
        val cutoff = LocalDate.now().minusDays(daysToKeep.toLong())
        listExportedFiles(config).forEach { (date, file) ->
            if (date.isBefore(cutoff)) file.delete()
        }
    }

    /**
     * Deletes a single export file for the given date.
     * Returns true if the file was deleted, false if it didn't exist.
     */
    fun deleteExport(date: LocalDate, config: ExportConfig): Boolean {
        val file = File(getExportDirectory(config), getFilenameForDate(date))
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
