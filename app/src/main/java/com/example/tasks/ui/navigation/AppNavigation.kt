package com.example.tasks.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.tasks.TasksApplication
import com.example.tasks.ui.screens.HomeScreen
import com.example.tasks.ui.screens.SettingsScreen
import com.example.tasks.ui.screens.TaskEditScreen
import com.example.tasks.ui.screens.TaskListScreen
import com.example.tasks.ui.viewmodel.SettingsViewModel
import com.example.tasks.ui.viewmodel.SettingsViewModelFactory
import com.example.tasks.ui.viewmodel.TaskViewModel
import com.example.tasks.ui.viewmodel.TaskViewModelFactory

@Composable
fun AppNavigation(navController: NavHostController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val taskViewModel: TaskViewModel = viewModel(
        factory = TaskViewModelFactory((context.applicationContext as TasksApplication).taskDataSource)
    )
    val settingsViewModel: SettingsViewModel =
        viewModel(factory = SettingsViewModelFactory(context))

    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable("home") {
            HomeScreen(
                taskViewModel = taskViewModel,
                settingsViewModel = settingsViewModel
            )
        }
        composable("taskList") {
            TaskListScreen(
                navController = navController,
                taskViewModel = taskViewModel
            )
        }
        composable("taskEdit/{taskId}") { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")
            TaskEditScreen(
                taskId = taskId,
                navController = navController,
                taskViewModel = taskViewModel
            )
        }
        composable("settings") {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                taskViewModel = taskViewModel
            )
        }
    }
}
