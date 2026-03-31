package com.agentstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentstudio.ui.theme.*
import com.agentstudio.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = viewModel()
) {
    val currentScreen by mainViewModel.currentScreen.collectAsState()
    
    // Animated gradient for bottom nav
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )
    
    Scaffold(
        containerColor = Background,
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Surface.copy(alpha = 0.95f),
                tonalElevation = 8.dp
            ) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp
                ) {
                    BottomNavItem.values().forEach { item ->
                        val isSelected = currentScreen == item.screen
                        
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { mainViewModel.navigateTo(item.screen) },
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .then(
                                            if (isSelected) {
                                                Modifier.background(
                                                    Brush.linearGradient(
                                                        colors = listOf(GradientPurple, GradientCyan),
                                                        start = Offset(animatedOffset - 200, 0f),
                                                        end = Offset(animatedOffset, 200f)
                                                    ),
                                                    CircleShape
                                                )
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label,
                                        tint = if (isSelected) Color.White else OnBackgroundMuted
                                    )
                                }
                            },
                            label = { 
                                Text(
                                    item.label,
                                    color = if (isSelected) Primary else OnBackgroundMuted
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                unselectedIconColor = OnBackgroundMuted,
                                selectedTextColor = Primary,
                                unselectedTextColor = OnBackgroundMuted,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                },
                label = "screenTransition"
            ) { screen ->
                when (screen) {
                    MainViewModel.Screen.AGENT -> AgentScreen()
                    MainViewModel.Screen.FILES -> FilesScreen()
                    MainViewModel.Screen.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}

enum class BottomNavItem(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val screen: MainViewModel.Screen
) {
    AGENT(
        label = "Agent",
        selectedIcon = Icons.Filled.SmartToy,
        unselectedIcon = Icons.Outlined.SmartToy,
        screen = MainViewModel.Screen.AGENT
    ),
    FILES(
        label = "Tệp tin",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        screen = MainViewModel.Screen.FILES
    ),
    SETTINGS(
        label = "Cài đặt",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        screen = MainViewModel.Screen.SETTINGS
    )
}
