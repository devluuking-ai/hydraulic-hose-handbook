package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.DetailScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.EbookViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: EbookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                EbookApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshNetworkState()
        viewModel.checkLockoutStatus()
    }

    override fun onStop() {
        super.onStop()
        // Auto-lock admin whenever app is closed or minimized
        viewModel.lockAdmin()
    }
}

@Composable
fun EbookApp(viewModel: EbookViewModel) {
    val navController = rememberNavController()
    val selectedChapter by viewModel.selectedChapter.collectAsState()
    val isAdminUnlocked by viewModel.isAdminUnlocked.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = Modifier.fillMaxSize()
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onChapterClick = { chapter ->
                    viewModel.selectChapter(chapter)
                    navController.navigate("detail")
                },
                onAdminUnlocked = {
                    navController.navigate("admin")
                }
            )
        }
        composable("detail") {
            val chapter = selectedChapter
            if (chapter != null) {
                DetailScreen(
                    chapter = chapter,
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            } else {
                HomeScreen(
                    viewModel = viewModel,
                    onChapterClick = { ch ->
                        viewModel.selectChapter(ch)
                        navController.navigate("detail")
                    },
                    onAdminUnlocked = {
                        navController.navigate("admin")
                    }
                )
            }
        }
        composable("admin") {
            if (isAdminUnlocked) {
                AdminScreen(
                    viewModel = viewModel,
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            } else {
                // If locked, immediately redirect back to home
                LaunchedEffect(Unit) {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            }
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
