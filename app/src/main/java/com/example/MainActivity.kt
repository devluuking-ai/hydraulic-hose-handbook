package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    }
}

@Composable
fun EbookApp(viewModel: EbookViewModel) {
    val navController = rememberNavController()
    val selectedChapter by viewModel.selectedChapter.collectAsState()

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
                onSettingsClick = {
                    navController.navigate("settings")
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
                    onSettingsClick = {
                        navController.navigate("settings")
                    }
                )
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
