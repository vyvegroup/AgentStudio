package com.agentstudio.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentstudio.ui.theme.*
import com.agentstudio.utils.Constants
import com.agentstudio.utils.ModelOption

private const val PREFS_NAME = "agent_studio_prefs"
private const val KEY_MODEL_ID = "selected_model_id"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onModelChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    
    var selectedModelId by remember { 
        mutableStateOf(prefs.getString(KEY_MODEL_ID, Constants.MODEL_ID) ?: Constants.MODEL_ID) 
    }
    var showModelSelector by remember { mutableStateOf(false) }
    
    // Animated gradient
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
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface.copy(alpha = 0.95f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GradientPurple, GradientCyan),
                                start = Offset(animatedOffset - 500, 0f),
                                end = Offset(animatedOffset, 500f)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Text(
                    "Cài đặt",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = OnBackground
                )
            }
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Model Selection Card
            item {
                SettingsCard(
                    title = "Mô hình AI",
                    subtitle = "Chọn mô hình AI mặc định",
                    icon = Icons.Filled.Psychology,
                    iconColor = Primary
                ) {
                    Column {
                        // Current model display
                        val currentModel = Constants.AVAILABLE_MODELS.find { it.id == selectedModelId }
                        
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showModelSelector = true },
                            shape = RoundedCornerShape(16.dp),
                            color = CardBackground
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        currentModel?.name ?: "Unknown",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = OnBackground
                                    )
                                    Text(
                                        currentModel?.description ?: "",
                                        fontSize = 12.sp,
                                        color = OnBackgroundMuted
                                    )
                                }
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Select",
                                    tint = OnBackgroundMuted
                                )
                            }
                        }
                    }
                }
            }
            
            // About Card
            item {
                SettingsCard(
                    title = "Thông tin ứng dụng",
                    subtitle = "Phiên bản ${Constants.APP_VERSION}",
                    icon = Icons.Filled.Info,
                    iconColor = GradientCyan
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Outlined.Code,
                            title = "GitHub",
                            subtitle = "github.com/vyvegroup/AgentStudio",
                            onClick = { /* Open GitHub */ }
                        )
                        
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = Color(0xFF2A2A45)
                        )
                        
                        SettingsItem(
                            icon = Icons.Outlined.Description,
                            title = "OpenRouter API",
                            subtitle = "Sử dụng API từ OpenRouter",
                            onClick = { }
                        )
                    }
                }
            }
            
            // Features Card
            item {
                SettingsCard(
                    title = "Tính năng",
                    subtitle = "Khả năng của Agent",
                    icon = Icons.Filled.AutoAwesome,
                    iconColor = Tertiary
                ) {
                    Column {
                        FeatureItem(
                            icon = Icons.Filled.Folder,
                            title = "File Management",
                            description = "Tạo, đọc, sửa, xóa file"
                        )
                        
                        FeatureItem(
                            icon = Icons.Filled.Language,
                            title = "Web Search",
                            description = "Tìm kiếm thông tin web"
                        )
                        
                        FeatureItem(
                            icon = Icons.Filled.Image,
                            title = "Image Search",
                            description = "Tìm kiếm hình ảnh Gelbooru"
                        )
                    }
                }
            }
        }
    }
    
    // Model Selector Dialog
    if (showModelSelector) {
        ModelSelectorDialog(
            models = Constants.AVAILABLE_MODELS,
            selectedModelId = selectedModelId,
            onModelSelected = { modelId ->
                selectedModelId = modelId
                prefs.edit().putString(KEY_MODEL_ID, modelId).apply()
                onModelChanged(modelId)
                showModelSelector = false
            },
            onDismiss = { showModelSelector = false }
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardBackground
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconColor.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = OnBackground
                    )
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = OnBackgroundMuted
                    )
                }
            }
            
            // Content
            content()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = OnBackgroundMuted,
            modifier = Modifier.size(22.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnBackground
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = OnBackgroundMuted
            )
        }
        
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = OnBackgroundMuted.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun FeatureItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Primary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(18.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = OnBackground
            )
            Text(
                description,
                fontSize = 12.sp,
                color = OnBackgroundMuted
            )
        }
    }
}

@Composable
fun ModelSelectorDialog(
    models: List<ModelOption>,
    selectedModelId: String,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Chọn mô hình AI",
                fontWeight = FontWeight.Bold,
                color = OnBackground
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(models) { model ->
                    val isSelected = model.id == selectedModelId
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModelSelected(model.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Primary.copy(alpha = 0.2f) else CardBackground,
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(1.dp, Primary)
                        } else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.name,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (isSelected) Primary else OnBackground
                                )
                                Text(
                                    model.description,
                                    fontSize = 11.sp,
                                    color = OnBackgroundMuted
                                )
                            }
                            
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = Primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", color = Primary)
            }
        },
        containerColor = Surface,
        titleContentColor = OnBackground,
        textContentColor = OnBackground
    )
}
