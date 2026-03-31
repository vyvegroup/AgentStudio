package com.agentstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentstudio.AgentStudioApp
import com.agentstudio.data.local.LocalModel
import com.agentstudio.data.local.LocalModelManager
import com.agentstudio.data.local.LocalModels
import com.agentstudio.data.model.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSheet(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    localModelManager: LocalModelManager? = null
) {
    val scope = rememberCoroutineScope()
    
    // Safe handling of localModelManager
    val manager = localModelManager ?: AgentStudioApp.instance.localModelManager
    
    // Download states
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    
    // Check if model is downloaded
    var isLocalDownloaded by remember { mutableStateOf(false) }
    
    // Refresh download status
    LaunchedEffect(Unit) {
        isLocalDownloaded = manager?.isModelDownloaded(LocalModels.GEMMA_4B) ?: false
    }
    
    // Also refresh when dialog closes
    LaunchedEffect(isDownloading) {
        if (!isDownloading) {
            isLocalDownloaded = manager?.isModelDownloaded(LocalModels.GEMMA_4B) ?: false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121a),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3f3f5a))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFF6366F1))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Chọn AI Model",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF1F5F9)
                    )
                    Text(
                        text = "Cloud hoặc Local inference",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            
            // Cloud Models Section
            Text(
                text = "☁️ Cloud AI (Online)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8B5CF6),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FREE_MODELS) { model ->
                    ModelCard(
                        model = model,
                        isSelected = selectedModel == model.id,
                        onClick = { 
                            onModelSelected(model.id)
                            onDismiss()
                        }
                    )
                }
                
                // Local AI Section
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "💻 Local AI (Offline)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
                
                item {
                    LocalModelCard(
                        model = LOCAL_MODEL,
                        isSelected = selectedModel == LOCAL_MODEL.id,
                        isDownloaded = isLocalDownloaded,
                        isDownloading = isDownloading,
                        downloadProgress = downloadProgress,
                        downloadError = downloadError,
                        onSelect = {
                            if (isLocalDownloaded) {
                                onModelSelected(LOCAL_MODEL.id)
                                onDismiss()
                            } else {
                                showDownloadDialog = true
                            }
                        },
                        onDownload = {
                            showDownloadDialog = true
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
    
    // Download Dialog
    if (showDownloadDialog) {
        DownloadModelDialog(
            model = LocalModels.GEMMA_4B,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            downloadError = downloadError,
            onDismiss = { 
                showDownloadDialog = false
                downloadError = null
            },
            onStartDownload = {
                scope.launch {
                    isDownloading = true
                    downloadProgress = 0f
                    downloadError = null
                    
                    manager?.downloadModel(LocalModels.GEMMA_4B)?.collect { progress ->
                        when (progress) {
                            is LocalModelManager.DownloadProgress.Progress -> {
                                downloadProgress = progress.progress
                            }
                            is LocalModelManager.DownloadProgress.Completed -> {
                                isDownloading = false
                                downloadProgress = 1f
                                isLocalDownloaded = true
                                kotlinx.coroutines.delay(500)
                                showDownloadDialog = false
                            }
                            is LocalModelManager.DownloadProgress.Error -> {
                                isDownloading = false
                                downloadError = progress.message
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { onClick() },
        color = if (isSelected) Color(0xFF3730A3) else Color(0xFF1e1e2e),
        tonalElevation = if (isSelected) 0.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with gradient
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) {
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6366F1), Color(0xFF4F46E5))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF2a2a3e), Color(0xFF1f1f2e))
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFF8B5CF6),
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isSelected) Color.White else Color(0xFFF1F5F9)
                )
                Text(
                    text = model.provider,
                    fontSize = 12.sp,
                    color = if (isSelected) Color(0xFFC7D2FE) else Color(0xFF64748B)
                )
            }
            
            if (model.isFree) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF059669).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "FREE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8B5CF6)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalModelCard(
    model: ModelInfo,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadError: String?,
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button
            ) {
                if (isDownloaded && !isDownloading) {
                    onSelect()
                } else if (!isDownloading) {
                    onDownload()
                }
            },
        color = when {
            isDownloading -> Color(0xFF1a2e1a)
            isSelected && isDownloaded -> Color(0xFF059669).copy(alpha = 0.2f)
            else -> Color(0xFF1e1e2e)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isDownloading -> Brush.linearGradient(
                                    colors = listOf(Color(0xFF059669), Color(0xFF047857))
                                )
                                isDownloaded -> Brush.linearGradient(
                                    colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                                )
                                else -> Brush.linearGradient(
                                    colors = listOf(Color(0xFF2a2a3e), Color(0xFF1f1f2e))
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = downloadProgress,
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Default.Check else Icons.Outlined.Download,
                            contentDescription = null,
                            tint = if (isDownloaded) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (isDownloaded || isDownloading) Color(0xFFF1F5F9) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (!isDownloaded && !isDownloading) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = model.downloadSize ?: "",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    
                    Text(
                        text = when {
                            isDownloading -> "Đang tải... ${(downloadProgress * 100).toInt()}%"
                            downloadError != null -> "Lỗi: ${downloadError.take(30)}"
                            isDownloaded -> "✓ Sẵn sàng chạy offline"
                            else -> "Tap để tải về thiết bị"
                        },
                        fontSize = 12.sp,
                        color = when {
                            isDownloading -> Color(0xFF10B981)
                            downloadError != null -> Color(0xFFEF4444)
                            isDownloaded -> Color(0xFF10B981)
                            else -> Color(0xFF64748B)
                        }
                    )
                }
                
                if (isDownloaded) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF059669).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "LOCAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            
            // Progress bar
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF1f2937)
                )
            }
            
            // Error message
            if (downloadError != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = downloadError,
                    fontSize = 11.sp,
                    color = Color(0xFFEF4444),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun DownloadModelDialog(
    model: LocalModel,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadError: String?,
    onDismiss: () -> Unit,
    onStartDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        containerColor = Color(0xFF1e1e2e),
        shape = RoundedCornerShape(24.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF10B981), Color(0xFF059669))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        progress = downloadProgress,
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = if (isDownloading) "Đang tải..." else "Tải Model Local",
                color = Color(0xFFF1F5F9),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isDownloading) {
                    // Progress display
                    Text(
                        text = "${(downloadProgress * 100).toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = downloadProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1f2937)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Vui lòng không đóng app",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                } else {
                    Text(
                        text = model.name,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Kích thước: ~${model.sizeBytes / 1_000_000_000}GB\n\n" +
                               "Model sẽ chạy hoàn toàn trên thiết bị của bạn.\n" +
                               "Không cần internet sau khi tải.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    
                    if (downloadError != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Lỗi: $downloadError",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isDownloading) {
                Button(
                    onClick = onStartDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tải về", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (!isDownloading) {
                TextButton(onClick = onDismiss) {
                    Text("Hủy", color = Color(0xFF64748B))
                }
            }
        }
    )
}
