package com.example.tasks.data.services

import android.util.Log
import com.example.tasks.data.repositories.LogRepository
import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.serialization.TaskSerializer
import org.json.JSONArray
import org.json.JSONObject

/**
 * TaskImportExportService handles bulk data conversion for file operations.
 */
class TaskImportExportService(
    private val repository: TaskRepository,
    private val logRepository: LogRepository // Added for error tracking
) {

    /**
     * Parses JSON string and adds tasks to repository.
     */
    suspend fun importFromJson(jsonString: String): Int {
        logRepository.log(Log.INFO, "ImportExport", "Starting data import...")
        return try {
            val jsonArray = JSONArray(jsonString)
            var count = 0
            for (i in 0 until jsonArray.length()) {
                try {
                    val obj = jsonArray.getJSONObject(i)
                    val task = TaskSerializer.deserialize(obj.toString())
                    repository.addTask(task)
                    count++
                } catch (e: Exception) {
                    logRepository.log(
                        Log.ERROR,
                        "ImportExport",
                        "Failed to parse task at index $i: ${e.message}"
                    )
                }
            }
            logRepository.log(
                Log.INFO,
                "ImportExport",
                "Import completed. Successfully added $count tasks."
            )
            count
        } catch (e: Exception) {
            logRepository.log(
                Log.ERROR,
                "ImportExport",
                "FATAL: Import failed (malformed JSON?): ${e.message}"
            )
            0
        }
    }

    /**
     * Converts all repository tasks into a formatted JSON string.
     */
    suspend fun exportToJson(): String {
        logRepository.log(Log.INFO, "ImportExport", "Preparing data export...")
        return try {
            val tasks = repository.getAllTasks()
            val jsonArray = JSONArray()
            tasks.forEach { task ->
                jsonArray.put(JSONObject(TaskSerializer.serialize(task)))
            }
            logRepository.log(Log.INFO, "ImportExport", "Export generated (${tasks.size} tasks).")
            jsonArray.toString(2)
        } catch (e: Exception) {
            logRepository.log(Log.ERROR, "ImportExport", "Export failed: ${e.message}")
            "[]"
        }
    }
}
