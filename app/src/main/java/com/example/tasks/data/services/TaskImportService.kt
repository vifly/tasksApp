package com.example.tasks.data.services

import com.example.tasks.data.repositories.TaskRepository
import com.example.tasks.data.serialization.TaskSerializer
import org.json.JSONArray

/**
 * TaskImportService handles the logic of importing tasks from external JSON data.
 */
class TaskImportService(private val repository: TaskRepository) {

    /**
     * Imports tasks from a JSON array string.
     * Returns the count of successfully imported tasks.
     */
    suspend fun importFromJson(jsonString: String): Int {
        return try {
            val jsonArray = JSONArray(jsonString)
            var count = 0
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                // Use serializer to convert JSONObject string back to Task
                val task = TaskSerializer.deserialize(obj.toString())
                repository.addTask(task)
                count++
            }
            count
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}
