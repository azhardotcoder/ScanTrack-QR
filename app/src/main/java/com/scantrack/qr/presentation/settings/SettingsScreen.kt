package com.scantrack.qr.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scantrack.qr.presentation.components.SectionHeader

@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        SectionHeader(title = "Settings")
        
        // Placeholder for settings content
    }
}
