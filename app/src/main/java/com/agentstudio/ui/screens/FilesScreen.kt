package com.agentstudio.ui.screens

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentstudio.data.model.FileItem
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
    
    // Load files on first composition
    LaunchedEffect(Unit) {
        mainViewModel.loadFiles()
    }
    
    // Refresh files when directory changes
    LaunchedEffect(workingDirectory) {
        mainViewModel.loadFiles()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quản lý tệp")
                        Text(
                            text = workingDirectory,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { mainViewModel.loadFiles() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Làm mới")
                    }
                    IconButton(onClick = { showCreateFolderDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Tạo thư mục")
                    }
                    IconButton(onClick = { showCreateFileDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Tạo tệp")
                    }
                },
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
        ) {
            // Breadcrumb navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { mainViewModel.navigateToParent() },
                    enabled = workingDirectory != "/"
                ) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Thư mục cha")
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = workingDirectory,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Divider()
            
            // File list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Thư mục trống",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(files) { file ->
                        FileItemRow(
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
                            onLongClick = { fileToDelete = file },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
    
    // Create File Dialog
    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { fileName ->
                showCreateFileDialog = false
                // Create file logic
            }
        )
    }
    
    // Create Folder Dialog
    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { folderName ->
                showCreateFolderDialog = false
                // Create folder logic
            }
        )
    }
    
    // Delete Confirmation Dialog
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Xác nhận xóa") },
            text = { 
                Text("Bạn có chắc muốn xóa \"${file.name}\" không?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        fileToDelete = null
                        // Delete file logic
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var lastClickTime by remember { mutableLongStateOf(0L) }
    
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
                    text = FileUtils.formatSize(file.size),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Filled.Folder else getFileIcon(file.extension),
                contentDescription = null,
                tint = if (file.isDirectory) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            if (file.isFile) {
                Text(
                    text = FileUtils.formatDate(file.lastModified),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            .then(
                if (isSelected) {
                    Modifier.padding(horizontal = 8.dp)
                } else {
                    Modifier
                }
            ),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun getFileIcon(extension: String): androidx.compose.ui.graphics.vector.ImageVector {
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
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo tệp mới") },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("Tên tệp") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fileName) },
                enabled = fileName.isNotBlank()
            ) {
                Text("Tạo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo thư mục mới") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Tên thư mục") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Tạo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
