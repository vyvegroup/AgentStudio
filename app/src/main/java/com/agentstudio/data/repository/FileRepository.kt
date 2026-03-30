package com.agentstudio.data.repository

import android.content.Context
import android.util.Log
import com.agentstudio.data.model.FileItem
import com.agentstudio.domain.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class FileRepository(private val context: Context) {
    
    private var currentDirectory: File = context.filesDir
    
    fun getCurrentDirectory(): File = currentDirectory
    
    fun setCurrentDirectory(directory: File): Boolean {
        return if (directory.exists() && directory.isDirectory) {
            currentDirectory = directory
            true
        } else {
            false
        }
    }
    
    suspend fun listDirectory(path: String? = null): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val dir = path?.let { File(it) } ?: currentDirectory
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext Result.failure(IOException("Directory does not exist: ${dir.absolutePath}"))
            }
            
            val files = dir.listFiles()
                ?.filter { !it.isHidden }
                ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
                ?.map { FileItem(it) }
                ?: emptyList()
            
            Result.success(files)
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
            
            if (dir.exists()) {
                return@withContext ToolResult(
                    success = false,
                    error = "Directory already exists: ${dir.absolutePath}"
                )
            }
            
            val created = dir.mkdirs()
            if (created) {
                ToolResult(
                    success = true,
                    output = "Directory created: ${dir.absolutePath}"
                )
            } else {
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
            
            if (file.exists()) {
                return@withContext ToolResult(
                    success = false,
                    error = "File already exists: ${file.absolutePath}"
                )
            }
            
            // Create parent directories if needed
            file.parentFile?.mkdirs()
            
            file.writeText(content)
            ToolResult(
                success = true,
                output = "File created: ${file.absolutePath}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creating file", e)
            ToolResult(success = false, error = e.message ?: "Unknown error")
        }
    }
    
    suspend fun readFile(path: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            if (!file.exists()) {
                return@withContext ToolResult(
                    success = false,
                    error = "File does not exist: ${file.absolutePath}"
                )
            }
            
            if (file.isDirectory) {
                return@withContext ToolResult(
                    success = false,
                    error = "Path is a directory, not a file: ${file.absolutePath}"
                )
            }
            
            val content = file.readText()
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
            ToolResult(success = false, error = e.message ?: "Unknown error")
        }
    }
    
    suspend fun editFile(path: String, oldContent: String, newContent: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
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
            ToolResult(success = false, error = e.message ?: "Unknown error")
        }
    }
    
    suspend fun writeFile(path: String, content: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
            // Create parent directories if needed
            file.parentFile?.mkdirs()
            
            file.writeText(content)
            ToolResult(
                success = true,
                output = "File written: ${file.absolutePath}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error writing file", e)
            ToolResult(success = false, error = e.message ?: "Unknown error")
        }
    }
    
    suspend fun deleteFile(path: String): ToolResult = withContext(Dispatchers.IO) {
        try {
            val file = if (File(path).isAbsolute) {
                File(path)
            } else {
                File(currentDirectory, path)
            }
            
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
            ToolResult(success = false, error = e.message ?: "Unknown error")
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
            ToolResult(success = false, error = e.message ?: "Unknown error")
        }
    }
    
    companion object {
        private const val TAG = "FileRepository"
    }
}
