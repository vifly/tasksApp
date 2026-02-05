package com.example.tasks.data.serialization

import com.example.tasks.data.Task
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * TaskSerializer handles the conversion between Task objects and JSON.
 */
object TaskSerializer {

    fun serialize(task: Task): String {
        val json = JSONObject()
        json.put("uuid", task.uuid)
        json.put("content", task.content)
        json.put("is_pinned", task.isPinned)
        json.put("created_at", task.createdAt.time)
        json.put("updated_at", task.updatedAt.time)
        json.put("tags", JSONArray(task.tags))
        json.put("custom_sort_order", task.customSortOrder)
        return json.toString()
    }

    fun deserialize(jsonString: String): Task {
        val json = JSONObject(jsonString)
        return Task(
            uuid = json.optString("uuid", UUID.randomUUID().toString()),
            content = json.optString("content", ""),
            isPinned = json.optBoolean("is_pinned", false),
            createdAt = Date(json.optLong("created_at", System.currentTimeMillis())),
            updatedAt = Date(json.optLong("updated_at", System.currentTimeMillis())),
            tags = json.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList(),
            customSortOrder = json.optInt("custom_sort_order", 0)
        )
    }
}
