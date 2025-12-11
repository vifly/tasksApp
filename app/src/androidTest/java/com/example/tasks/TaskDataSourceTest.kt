package com.example.tasks

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.tasks.data.Task
import com.example.tasks.data.db.TaskDataSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Date

@RunWith(AndroidJUnit4::class)
class TaskDataSourceTest {

    private lateinit var dataSource: TaskDataSource

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Use an in-memory database for testing
        dataSource = TaskDataSource(context)
        // Clear all tasks before each test
        dataSource.deleteTasks(dataSource.getAllTasks())
    }

    @After
    fun tearDown() {
        // The in-memory database will be cleaned up automatically, 
        // but if it were a file-based DB, we would close it here.
    }

    @Test
    fun testAddTaskAndGetTask() {
        val task = Task(id = 1, content = "Test Task", createdAt = Date(), updatedAt = Date())
        dataSource.addTask(task)

        val tasks = dataSource.getAllTasks()
        assertEquals(1, tasks.size)
        val retrievedTask = tasks.first()
        assertEquals("Test Task", retrievedTask.content)
    }

    @Test
    fun testUpdateTask() {
        val task =
            Task(id = 2, content = "Original Content", createdAt = Date(), updatedAt = Date())
        dataSource.addTask(task)

        val updatedTask = task.copy(content = "Updated Content")
        dataSource.updateTask(updatedTask)

        val tasks = dataSource.getAllTasks()
        val retrievedTask = tasks.find { it.id == 2L }
        assertNotNull(retrievedTask)
        assertEquals("Updated Content", retrievedTask?.content)
    }

    @Test
    fun testDeleteTask() {
        val task = Task(id = 3, content = "To be deleted", createdAt = Date(), updatedAt = Date())
        dataSource.addTask(task)

        var tasks = dataSource.getAllTasks()
        assertTrue(tasks.any { it.id == 3L })

        dataSource.deleteTask(task)

        tasks = dataSource.getAllTasks()
        val retrievedTask = tasks.find { it.id == 3L }
        assertNull(retrievedTask)
    }

    @Test
    fun testDeleteMultipleTasks() {
        val task1 = Task(id = 4, content = "Task 1", createdAt = Date(), updatedAt = Date())
        val task2 = Task(id = 5, content = "Task 2", createdAt = Date(), updatedAt = Date())
        val task3 = Task(id = 6, content = "Task 3", createdAt = Date(), updatedAt = Date())
        dataSource.addTask(task1)
        dataSource.addTask(task2)
        dataSource.addTask(task3)

        assertEquals(3, dataSource.getAllTasks().size)

        dataSource.deleteTasks(listOf(task1, task3))

        val remainingTasks = dataSource.getAllTasks()
        assertEquals(1, remainingTasks.size)
        assertEquals(5L, remainingTasks.first().id)
    }
}
