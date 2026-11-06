package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.viewmodel.EbookViewModel

/**
 * SettingsScreen is secured and redirected to AdminScreen.
 * Google Drive configuration is restricted exclusively to authenticated admins.
 * Normal users cannot view or modify these settings.
 */
@Composable
fun SettingsScreen(
    viewModel: EbookViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    val isAdminUnlocked by viewModel.isAdminUnlocked.collectAsState()

    if (isAdminUnlocked) {
        AdminScreen(
            viewModel = viewModel,
            onBackClick = onBackClick,
            modifier = modifier
        )
    } else {
        // Normal users cannot access settings; return back immediately
        onBackClick()
    }
}
