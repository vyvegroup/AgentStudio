package com.agentstudio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentstudio.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = viewModel()
) {
    val currentScreen by mainViewModel.currentScreen.collectAsState()
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavItem.values().forEach { item ->
                    NavigationBarItem(
                        selected = currentScreen == item.screen,
                        onClick = { mainViewModel.navigateTo(item.screen) },
                        icon = {
                            Icon(
                                imageVector = if (currentScreen == item.screen) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                MainViewModel.Screen.AGENT -> AgentScreen()
                MainViewModel.Screen.FILES -> FilesScreen()
                MainViewModel.Screen.SETTINGS -> SettingsScreen()
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
        label = "Tệp",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var apiKeyVisible by remember { mutableStateOf(false) }
    var apiKey by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf(TextFieldValue("")) }
    var temperature by remember { mutableStateOf(0.7f) }
    var maxTokens by remember { mutableStateOf(4096) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // API Settings Section
            Text(
                text = "Cấu hình API",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None 
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (apiKeyVisible) "Ẩn" else "Hiện"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = "nvidia/nemotron-3-super-120b-a12b:free",
                onValueChange = { },
                label = { Text("Model") },
                singleLine = true,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Agent Settings Section
            Text(
                text = "Cấu hình Agent",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedTextField(
                value = systemPrompt,
                onValueChange = { systemPrompt = it },
                label = { Text("System Prompt") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Temperature: ${String.format("%.1f", temperature)}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Slider(
                value = temperature,
                onValueChange = { temperature = it },
                valueRange = 0f..2f,
                steps = 20,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Max Tokens: $maxTokens",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Slider(
                value = maxTokens.toFloat(),
                onValueChange = { maxTokens = it.toInt() },
                valueRange = 256f..8192f,
                steps = 31,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { 
                        apiKey = ""
                        systemPrompt = TextFieldValue("")
                        temperature = 0.7f
                        maxTokens = 4096
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.RestartAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đặt lại")
                }
                
                Button(
                    onClick = { /* Save settings */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Lưu")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // About Section
            Text(
                text = "Giới thiệu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "AgentStudio",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Phiên bản 1.0.0",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Ứng dụng AI Agent hỗ trợ quản lý file và lập trình với khả năng gọi công cụ tự động.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
