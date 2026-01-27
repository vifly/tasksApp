package com.example.tasks.data

import java.util.Date
import java.util.UUID

data class Task(
    val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isPinned: Boolean = false,
    val customSortOrder: Int = 0
)