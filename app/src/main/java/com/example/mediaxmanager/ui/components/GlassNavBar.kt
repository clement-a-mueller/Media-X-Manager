package com.example.mediaxmanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassNavBar(
    selectedIndex: Int,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
    ) {
        // Base glass layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.15f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
        )

        // Top highlight — fake reflection line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.4f))
        )

        // Glossy sheen — upper half brighter
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.4f)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Circular highlight blobs (fake lens reflections)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.2f),
                    radius = size.width * 0.2f
                ),
                topLeft = Offset(size.width * 0.05f, 0f),
                size = Size(size.width * 0.4f, size.height * 0.6f)
            )
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.2f),
                    radius = size.width * 0.2f
                ),
                topLeft = Offset(size.width * 0.55f, 0f),
                size = Size(size.width * 0.4f, size.height * 0.6f)
            )
        }

        // Nav items
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassNavItem(
                icon = {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Home",
                        tint = Color.White
                    )
                },
                label = "Home",
                selected = selectedIndex == 0,
                onClick = onHomeClick
            )
            GlassNavItem(
                icon = {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                },
                label = "Settings",
                selected = selectedIndex == 1,
                onClick = onSettingsClick
            )
        }
    }
}

@Composable
fun GlassNavItem(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (selected) Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    )
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon()
        Text(
            label,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
}