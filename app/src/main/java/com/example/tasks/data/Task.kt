package com.example.tasks.data

import java.util.Date

data class Task(
    val id: Long = 0,
    val content: String,
    val tags: List<String> = emptyList(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isPinned: Boolean = false,
    val customSortOrder: Int = 0
)