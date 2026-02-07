package com.example.tasks.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.tasks.TasksApplication
import com.example.tasks.ui.screens.DebugLogScreen
import com.example.tasks.ui.screens.HomeScreen
import com.example.tasks.ui.screens.SettingsScreen
import com.example.tasks.ui.screens.TaskEditScreen
import com.example.tasks.ui.screens.TaskListScreen
import com.example.tasks.ui.viewmodel.DebugLogViewModel
import com.example.tasks.ui.viewmodel.DebugLogViewModelFactory
import com.example.tasks.ui.viewmodel.SettingsViewModel
import com.example.tasks.ui.viewmodel.SettingsViewModelFactory
import com.example.tasks.ui.viewmodel.TaskViewModel
import com.example.tasks.ui.viewmodel.TaskViewModelFactory

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as TasksApplication

    val taskViewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory(
            app.taskRepository,
            app.syncScheduler,
            app.taskImportExportService
        )
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app.settingsRepository, app.syncScheduler)
    )

    val debugLogViewModel: DebugLogViewModel = viewModel(
        factory = DebugLogViewModelFactory(app.logRepository)
    )

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(taskViewModel, settingsViewModel)
        }
        composable("tasks") {
            TaskListScreen(navController, taskViewModel)
        }
        composable("add_task") {
            TaskEditScreen(null, navController, taskViewModel)
        }
        composable("edit_task/{taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")
            TaskEditScreen(taskId, navController, taskViewModel)
        }
        composable("settings") {
            SettingsScreen(settingsViewModel, taskViewModel, navController)
        }
        composable("debug_logs") {
            DebugLogScreen(navController, debugLogViewModel)
        }
    }
}