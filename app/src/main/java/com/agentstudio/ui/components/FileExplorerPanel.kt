package com.agentstudio.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agentstudio.data.model.FileItem
import com.agentstudio.utils.FileUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerPanel(
    files: List<FileItem>,
    currentPath: String,
    onFileClick: (FileItem) -> Unit,
    onFileDoubleClick: (FileItem) -> Unit,
    onFileLongClick: (FileItem) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        // Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Lên")
                }
                
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Làm mới")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Divider()
        
        // File list
        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Thư mục trống",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .animateContentSize()
            ) {
                items(
                    items = files,
                    key = { it.path }
                ) { file ->
                    FileExplorerItem(
                        file = file,
                        onClick = { onFileClick(file) },
                        onDoubleClick = { onFileDoubleClick(file) },
                        onLongClick = { onFileLongClick(file) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileExplorerItem(
    file: FileItem,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    var isSelected by remember { mutableStateOf(false) }
    
    ListItem(
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            if (file.isFile) {
                Text(
                    text = "${FileUtils.formatSize(file.size)} • ${FileUtils.formatDate(file.lastModified)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = getFileIconForItem(file),
                contentDescription = null,
                tint = if (file.isDirectory)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        modifier = modifier
            .combinedClickable(
                onClick = {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastClickTime < 300) {
                        onDoubleClick()
                    } else {
                        onClick()
                    }
                    lastClickTime = currentTime
                },
                onLongClick = onLongClick
            )
            .animateContentSize(),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun getFileIconForItem(file: FileItem): androidx.compose.ui.graphics.vector.ImageVector {
    return if (file.isDirectory) {
        Icons.Filled.Folder
    } else {
        when (file.extension.lowercase()) {
            "kt", "java", "py", "js", "ts", "jsx", "tsx", "c", "cpp", "go", "rs" -> Icons.Filled.Code
            "html", "htm" -> Icons.Filled.Html
            "css", "scss" -> Icons.Filled.Css
            "json", "xml" -> Icons.Filled.DataObject
            "md", "txt" -> Icons.Filled.Description
            "png", "jpg", "jpeg", "gif", "webp", "svg" -> Icons.Filled.Image
            "pdf" -> Icons.Filled.PictureAsPdf
            "zip", "tar", "gz", "rar", "7z" -> Icons.Filled.Archive
            "mp3", "wav", "ogg" -> Icons.Filled.AudioFile
            "mp4", "avi", "mov", "mkv" -> Icons.Filled.VideoFile
            else -> Icons.Filled.InsertDriveFile
        }
    }
}
