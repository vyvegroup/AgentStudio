package com.agentstudio.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentstudio.data.model.FileItem
import com.agentstudio.ui.theme.*
import com.agentstudio.ui.viewmodel.MainViewModel
import com.agentstudio.utils.FileUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    mainViewModel: MainViewModel = viewModel()
) {
    val files by mainViewModel.files.collectAsState()
    val workingDirectory by mainViewModel.workingDirectory.collectAsState()
    val selectedFile by mainViewModel.selectedFile.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<FileItem?>(null) }
    
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
    
    // Load files on first composition
    LaunchedEffect(Unit) {
        mainViewModel.loadFiles()
    }
    
    // Refresh files when directory changes
    LaunchedEffect(workingDirectory) {
        mainViewModel.loadFiles()
    }
    
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
                                colors = listOf(GradientCyan, GradientGreen),
                                start = Offset(animatedOffset - 500, 0f),
                                end = Offset(animatedOffset, 500f)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(14.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Quản lý tệp",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = OnBackground
                    )
                    Text(
                        workingDirectory,
                        fontSize = 11.sp,
                        color = OnBackgroundMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Action buttons
                IconButton(
                    onClick = { mainViewModel.loadFiles() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Làm mới",
                        tint = OnBackgroundMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(
                    onClick = { showCreateFolderDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.CreateNewFolder,
                        contentDescription = "Tạo thư mục",
                        tint = OnBackgroundMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                IconButton(
                    onClick = { showCreateFileDialog = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Tạo tệp",
                        tint = OnBackgroundMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // Breadcrumb navigation
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceLight.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { mainViewModel.navigateToParent() },
                    enabled = workingDirectory != "/",
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = "Thư mục cha",
                        tint = if (workingDirectory != "/") Primary else OnBackgroundMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ChipBackground
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FolderOpen,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            workingDirectory.takeLast(30).let { if (it.length < workingDirectory.length) "…$it" else it },
                            fontSize = 12.sp,
                            color = OnBackground
                        )
                    }
                }
            }
        }
        
        // File list
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Outlined.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = OnBackgroundMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Thư mục trống",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnBackgroundMuted
                    )
                    Text(
                        text = "Nhấn + để tạo tệp mới",
                        fontSize = 12.sp,
                        color = OnBackgroundMuted.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files) { file ->
                    FileItemCard(
                        file = file,
                        isSelected = selectedFile?.path == file.path,
                        onClick = { mainViewModel.selectFile(file) },
                        onDoubleClick = {
                            if (file.isDirectory) {
                                mainViewModel.navigateToChild(file)
                            } else {
                                mainViewModel.selectFile(file)
                            }
                        },
                        onLongClick = { fileToDelete = file }
                    )
                }
            }
        }
    }
    
    // Create File Dialog
    if (showCreateFileDialog) {
        ModernCreateFileDialog(
            title = "Tạo tệp mới",
            label = "Tên tệp",
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { fileName ->
                showCreateFileDialog = false
            }
        )
    }
    
    // Create Folder Dialog
    if (showCreateFolderDialog) {
        ModernCreateFileDialog(
            title = "Tạo thư mục mới",
            label = "Tên thư mục",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                showCreateFolderDialog = false
            }
        )
    }
    
    // Delete Confirmation Dialog
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { 
                Text(
                    "Xác nhận xóa",
                    fontWeight = FontWeight.Bold,
                    color = OnBackground
                )
            },
            text = { 
                Text(
                    "Bạn có chắc muốn xóa \"${file.name}\" không?",
                    color = OnBackgroundMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = { fileToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Hủy", color = OnBackgroundMuted)
                }
            },
            containerColor = Surface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemCard(
    file: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    
    val iconColor = when {
        file.isDirectory -> GradientCyan
        file.extension.lowercase() in listOf("kt", "java", "py", "js", "ts") -> CodeFunction
        file.extension.lowercase() in listOf("json", "xml") -> CodeNumber
        file.extension.lowercase() in listOf("md", "txt") -> OnBackgroundMuted
        file.extension.lowercase() in listOf("png", "jpg", "jpeg", "gif") -> Tertiary
        else -> OnBackgroundMuted
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
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
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) Primary.copy(alpha = 0.15f) else CardBackground,
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
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(iconColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Filled.Folder else getFileIcon(file.extension),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = OnBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (file.isFile) {
                    Text(
                        text = "${FileUtils.formatSize(file.size)} • ${FileUtils.formatDate(file.lastModified)}",
                        fontSize = 11.sp,
                        color = OnBackgroundMuted
                    )
                }
            }
            
            // Arrow
            if (file.isDirectory) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = OnBackgroundMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun getFileIcon(extension: String): ImageVector {
    return when (extension.lowercase()) {
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

@Composable
fun ModernCreateFileDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                title,
                fontWeight = FontWeight.Bold,
                color = OnBackground
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label, color = OnBackgroundMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = OnBackgroundMuted.copy(alpha = 0.3f),
                    focusedTextColor = OnBackground,
                    unfocusedTextColor = OnBackground
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("Tạo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = OnBackgroundMuted)
            }
        },
        containerColor = Surface
    )
}
