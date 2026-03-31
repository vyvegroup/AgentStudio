package com.agentstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val downloadedModels by localModelManager?.downloadState?.collectAsState() ?: remember { mutableStateOf(emptyMap()) }
    val isLocalDownloaded = LocalModels.GEMMA_4B.let { 
        localModelManager?.isModelDownloaded(it) ?: false 
    }
    
    var showDownloadDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    
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
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(28.dp)
                )
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
                        isDownloaded = true,
                        onClick = { onModelSelected(model.id) }
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
                        onSelect = {
                            if (isLocalDownloaded) {
                                onModelSelected(LOCAL_MODEL.id)
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
            onDismiss = { showDownloadDialog = false },
            onDownload = {
                localModelManager?.let { manager ->
                    scope.launch {
                        isDownloading = true
                        manager.downloadModel(LocalModels.GEMMA_4B).collect { progress ->
                            when (progress) {
                                is LocalModelManager.DownloadProgress.Progress -> {
                                    downloadProgress = progress.progress
                                }
                                is LocalModelManager.DownloadProgress.Completed -> {
                                    isDownloading = false
                                    showDownloadDialog = false
                                    downloadProgress = 0f
                                }
                                is LocalModelManager.DownloadProgress.Error -> {
                                    isDownloading = false
                                    downloadProgress = 0f
                                }
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
    isDownloaded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) Color(0xFF3730A3) else Color(0xFF1a1a2e),
        border = if (isSelected) null else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) Color(0xFF4F46E5)
                        else Color(0xFF2a2a3e)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else Color(0xFF8B5CF6),
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
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
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF059669).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "FREE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            
            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
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
    onSelect: () -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        onClick = if (isDownloaded) onSelect else onDownload,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected && isDownloaded) Color(0xFF059669).copy(alpha = 0.3f) 
                else Color(0xFF1a1a2e)
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
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDownloaded) Color(0xFF059669)
                            else Color(0xFF2a2a3e)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            progress = downloadProgress,
                            color = Color(0xFF10B981),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Default.Check else Icons.Outlined.Download,
                            contentDescription = null,
                            tint = if (isDownloaded) Color.White else Color(0xFF64748B),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = model.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = if (isDownloaded) Color(0xFFF1F5F9) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (!isDownloaded) {
                            Text(
                                text = model.downloadSize ?: "",
                                fontSize = 11.sp,
                                color = Color(0xFFF59E0B)
                            )
                        }
                    }
                    Text(
                        text = if (isDownloaded) "✓ Sẵn sàng chạy offline" 
                               else "Tap để tải về thiết bị",
                        fontSize = 12.sp,
                        color = if (isDownloaded) Color(0xFF10B981) else Color(0xFF64748B)
                    )
                }
                
                if (isDownloaded) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF059669).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "LOCAL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            // Download progress bar
            if (isDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = downloadProgress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF10B981),
                    trackColor = Color(0xFF1f2937)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${(downloadProgress * 100).toInt()}% - Đang tải...",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DownloadModelDialog(
    model: LocalModel,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1a1a2e),
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF059669)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Tải Model Local",
                color = Color(0xFFF1F5F9),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = model.name,
                    color = Color(0xFF8B5CF6),
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kích thước: ${model.sizeBytes / 1_000_000_000}GB\n\n" +
                           "Model sẽ chạy hoàn toàn trên thiết bị của bạn, không cần internet sau khi tải. " +
                           "Bạn có thể xóa bất cứ lúc nào trong cài đặt.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onDownload()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tải về")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = Color(0xFF64748B))
            }
        }
    )
}
