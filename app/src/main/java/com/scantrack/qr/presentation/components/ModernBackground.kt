package com.scantrack.qr.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.scantrack.qr.presentation.theme.BlobBlue
import com.scantrack.qr.presentation.theme.BlobGreen
import com.scantrack.qr.presentation.theme.BlobPurple

/**
 * A custom background that simulates a modern mesh gradient/glassmorphism effect.
 * Provides a single, unified root background for screens.
 */
@Composable
fun ModernBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)) // Base clean light color
    ) {
        // Soft Blobs for Mesh Gradient Effect - Adjusted for smoothness
        Box(
            modifier = Modifier
                .size(450.dp)
                .offset(x = (-120).dp, y = (-80).dp)
                .blur(120.dp)
                .background(BlobBlue.copy(alpha = 0.6f), CircleShape)
        )
        
        Box(
            modifier = Modifier
                .size(350.dp)
                .align(androidx.compose.ui.Alignment.TopEnd)
                .offset(x = 120.dp, y = 100.dp)
                .blur(100.dp)
                .background(BlobGreen.copy(alpha = 0.5f), CircleShape)
        )

        Box(
            modifier = Modifier
                .size(400.dp)
                .align(androidx.compose.ui.Alignment.BottomStart)
                .offset(x = (-60).dp, y = 180.dp)
                .blur(140.dp)
                .background(BlobPurple.copy(alpha = 0.5f), CircleShape)
        )

        // Main Content Layer
        content()
    }
}
