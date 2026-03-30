package com.agentstudio.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun ToolCallIndicator(
    toolName: String,
    isExecuting: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation by rememberInfiniteTransition(label = "rotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .animateContentSize(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isExecuting) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = getToolDisplayName(toolName),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            
            if (isExecuting) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đang thực thi...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ToolCallProgress(
    toolName: String,
    progress: Float,
    status: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getToolIcon(toolName),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = getToolDisplayName(toolName),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun ToolCallStatus(
    toolName: String,
    isSuccess: Boolean,
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        color = if (isSuccess)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSuccess) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = if (isSuccess) "Thành công" else "Thất bại",
                modifier = Modifier.size(20.dp),
                tint = if (isSuccess)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = getToolDisplayName(toolName),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSuccess)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onErrorContainer
                )
                
                if (message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSuccess)
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        else
                            MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun getToolIcon(toolName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (toolName) {
        "create_file" -> Icons.Filled.Create
        "read_file" -> Icons.Filled.Visibility
        "edit_file" -> Icons.Filled.Edit
        "delete_file" -> Icons.Filled.Delete
        "search_files" -> Icons.Filled.Search
        "list_directory" -> Icons.Filled.FolderOpen
        "create_directory" -> Icons.Filled.CreateNewFolder
        else -> Icons.Filled.Build
    }
}

fun getToolDisplayName(toolName: String): String {
    return when (toolName) {
        "create_file" -> "Tạo tệp"
        "read_file" -> "Đọc tệp"
        "edit_file" -> "Sửa tệp"
        "delete_file" -> "Xóa tệp"
        "search_files" -> "Tìm kiếm tệp"
        "list_directory" -> "Liệt kê thư mục"
        "create_directory" -> "Tạo thư mục"
        else -> toolName.replace("_", " ").capitalize()
    }
}

@Composable
fun ToolCallList(
    toolCalls: List<ToolCallInfo>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.animateContentSize()
    ) {
        toolCalls.forEach { toolCall ->
            ToolCallIndicator(
                toolName = toolCall.name,
                isExecuting = toolCall.isExecuting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }
    }
}

data class ToolCallInfo(
    val id: String,
    val name: String,
    val arguments: String,
    val isExecuting: Boolean = false,
    val result: String? = null,
    val isSuccess: Boolean = true
)
