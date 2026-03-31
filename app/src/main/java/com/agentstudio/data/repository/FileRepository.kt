package com.agentstudio.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.agentstudio.data.model.FileItem
import com.agentstudio.domain.model.ToolResult
import com.agentstudio.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileRepository(private val context: Context) {
    
    private var currentDirectory: File
    
    init {
        // Try to use external storage first, fall back to app storage
        currentDirectory = try {
            val externalDir = File(Constants.DEFAULT_PROJECT_DIR)
            if (isExternalStorageWritable()) {
                if (!externalDir.exists()) {
                    val created = externalDir.mkdirs()
                    Log.d(TAG, "Creating project directory: ${externalDir.absolutePath}, success: $created")
                }
                externalDir
            } else {
                Log.w(TAG, "External storage not writable, using app storage")
                context.filesDir
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing directory", e)
            context.filesDir
        }
        
        Log.d(TAG, "Current directory: ${currentDirectory.absolutePath}")
        Log.d(TAG, "Directory exists: ${currentDirectory.exists()}")
        Log.d(TAG, "Directory is writable: ${currentDirectory.canWrite()}")
    }
    
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
    
    fun getCurrentDirectory(): File = currentDirectory
    
    fun setCurrentDirectory(directory: File): Boolean {
        return if (directory.exists() && directory.isDirectory) {
            currentDirectory = directory
            Log.d(TAG, "Changed directory to: ${directory.absolutePath}")
            true
        } else {
            Log.w(TAG, "Failed to change directory: ${directory.absolutePath}")
            false
        }
    }
    
    suspend fun listDirectory(path: String? = null): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val dir = path?.let { File(it) } ?: currentDirectory
            Log.d(TAG, "Listing directory: ${dir.absolutePath}")
            
            if (!dir.exists()) {
                Log.w(TAG, "Directory does not exist: ${dir.absolutePath}")
                return@withContext Result.failure(IOException("Directory does not exist: ${dir.absolutePath}"))
            }
            
            if (!dir.isDirectory) {
                Log.w(TAG, "Path is not a directory: ${dir.absolutePath}")
                return@withContext Result.failure(IOException("Path is not a directory: ${dir.absolutePath}"))
            }
            
            if (!dir.canRead()) {
                Log.w(TAG, "Cannot read directory: ${dir.absolutePath}")
                return@withContext Result.failure(IOException("Cannot read directory: ${dir.absolutePath}"))
            }
            
            val files = dir.listFiles()
            if (files == null) {
                Log.w(TAG, "listFiles() returned null for: ${dir.absolutePath}")
                return@withContext Result.success(emptyList())
            }
            
            val result = files
                .filter { !it.isHidden }
                .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                .map { FileItem(it) }
            
            Log.d(TAG, "Found ${result.size} items")
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Error listing directory", e)
            Result.failure(e)
        }
    }
    
    suspend fun createDirectory(path: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val dir = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            Log.d(TAG, "Creating directory: ${dir.absolutePath}")
            
            if (dir.exists()) {
                Log.w(TAG, "Directory already exists: ${dir.absolutePath}")
                return@withContext ToolResult(
                    success = false,
                    error = "Directory already exists: ${dir.absolutePath}"
                )
            }
            
            val created = dir.mkdirs()
            if (created) {
                Log.d(TAG, "Directory created successfully: ${dir.absolutePath}")
                ToolResult(
                    success = true,
                    output = "Directory created: ${dir.absolutePath}"
                )
            } else {
                Log.w(TAG, "Failed to create directory: ${dir.absolutePath}")
                ToolResult(
                    success = false,
                    error = "Failed to create directory: ${dir.absolutePath}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating directory", e)
            ToolResult(success = false, error = e.message ?: "Unknown error")
        }
    }
    
    suspend fun createFile(path: String, content: String = ""): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            Log.d(TAG, "Creating file: ${file.absolutePath}")
            Log.d(TAG, "Content length: ${content.length}")
            
            if (file.exists()) {
                Log.w(TAG, "File already exists: ${file.absolutePath}")
                return@withContext ToolResult(
                    success = false,
                    error = "File already exists: ${file.absolutePath}"
                )
            }
            
            // Create parent directories if needed
            val parentCreated = file.parentFile?.mkdirs() ?: true
            Log.d(TAG, "Parent directories created: $parentCreated")
            
            file.writeText(content)
            
            Log.d(TAG, "File created successfully: ${file.absolutePath}")
            ToolResult(
                success = true,
                output = "File created: ${file.absolutePath} (${content.length} chars)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating file", e)
            ToolResult(success = false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    suspend fun readFile(path: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            Log.d(TAG, "Reading file: ${file.absolutePath}")
            
            if (!file.exists()) {
                Log.w(TAG, "File does not exist: ${file.absolutePath}")
                return@withContext ToolResult(
                    success = false,
                    error = "File does not exist: ${file.absolutePath}"
                )
            }
            
            if (file.isDirectory) {
                Log.w(TAG, "Path is a directory: ${file.absolutePath}")
                return@withContext ToolResult(
                    success = false,
                    error = "Path is a directory: ${file.absolutePath}"
                )
            }
            
            if (!file.canRead()) {
                Log.w(TAG, "Cannot read file: ${file.absolutePath}")
                return@withContext ToolResult(
                    success = false,
                    error = "Cannot read file: ${file.absolutePath}"
                )
            }
            
            val content = file.readText()
            Log.d(TAG, "File read successfully: ${content.length} chars")
            
            ToolResult(
                success = true,
                output = content,
                metadata = mapOf(
                    "path" to file.absolutePath,
                    "size" to file.length().toString()
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error reading file", e)
            ToolResult(success = false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    suspend fun editFile(path: String, oldContent: String, newContent: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            Log.d(TAG, "Editing file: ${file.absolutePath}")
            
            if (!file.exists()) {
                return@withContext ToolResult(
                    success = false,
                    error = "File does not exist: ${file.absolutePath}"
                )
            }
            
            val content = file.readText()
            if (!content.contains(oldContent)) {
                return@withContext ToolResult(
                    success = false,
                    error = "Old content not found in file"
                )
            }
            
            val newFileContent = content.replace(oldContent, newContent)
            file.writeText(newFileContent)
            
            ToolResult(
                success = true,
                output = "File edited successfully: ${file.absolutePath}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error editing file", e)
            ToolResult(success = false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    suspend fun writeFile(path: String, content: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            Log.d(TAG, "Writing file: ${file.absolutePath}")
            
            // Create parent directories if needed
            file.parentFile?.mkdirs()
            
            file.writeText(content)
            ToolResult(
                success = true,
                output = "File written: ${file.absolutePath}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file", e)
            ToolResult(success = false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    suspend fun deleteFile(path: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            Log.d(TAG, "Deleting: ${file.absolutePath}")
            
            if (!file.exists()) {
                return@withContext ToolResult(
                    success = false,
                    error = "File does not exist: ${file.absolutePath}"
                )
            }
            
            val deleted = if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
            
            if (deleted) {
                ToolResult(
                    success = true,
                    output = "Deleted: ${file.absolutePath}"
                )
            } else {
                ToolResult(
                    success = false,
                    error = "Failed to delete: ${file.absolutePath}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            ToolResult(success = false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    suspend fun searchFiles(query: String, directory: String? = null): ToolResult = withContext(Dispatchers.IO) {
        try {
            val searchDir = directory?.let { File(it) } ?: currentDirectory
            
            if (!searchDir.exists() || !searchDir.isDirectory) {
                return@withContext ToolResult(
                    success = false,
                    error = "Directory does not exist: ${searchDir.absolutePath}"
                )
            }
            
            val results = mutableListOf<String>()
            searchDir.walkTopDown()
                .onEnter { !it.isHidden }
                .forEach { file ->
                    if (file.name.contains(query, ignoreCase = true)) {
                        results.add(file.absolutePath)
                    }
                }
            
            ToolResult(
                success = true,
                output = if (results.isEmpty()) {
                    "No files found matching '$query'"
                } else {
                    "Found ${results.size} files:\n${results.joinToString("\n")}"
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching files", e)
            ToolResult(success = false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }
    
    companion object {
        private const val TAG = "FileRepository"
    }
}
