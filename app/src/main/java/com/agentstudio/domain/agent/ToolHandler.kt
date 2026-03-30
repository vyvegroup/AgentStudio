package com.agentstudio.domain.agent

import android.util.Log
import com.agentstudio.data.repository.FileRepository
import com.agentstudio.domain.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolHandler(private val fileRepository: FileRepository) {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun executeTool(name: String, arguments: String): ToolResult {
        Log.d(TAG, "Executing tool: $name with arguments: $arguments")
        
        return when (name) {
            "create_file" -> executeCreateFile(arguments)
            "read_file" -> executeReadFile(arguments)
            "edit_file" -> executeEditFile(arguments)
            "delete_file" -> executeDeleteFile(arguments)
            "search_files" -> executeSearchFiles(arguments)
            "list_directory" -> executeListDirectory(arguments)
            "create_directory" -> executeCreateDirectory(arguments)
            else -> ToolResult.error("Unknown tool: $name")
        }
    }
    
    private suspend fun executeCreateFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: path")
            val content = params["content"]?.jsonPrimitive?.content ?: ""
            
            fileRepository.createFile(path, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing create_file", e)
            ToolResult.error(e.message ?: "Failed to create file")
        }
    }
    
    private suspend fun executeReadFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: path")
            
            fileRepository.readFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing read_file", e)
            ToolResult.error(e.message ?: "Failed to read file")
        }
    }
    
    private suspend fun executeEditFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: path")
            val oldContent = params["old_content"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: old_content")
            val newContent = params["new_content"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: new_content")
            
            fileRepository.editFile(path, oldContent, newContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing edit_file", e)
            ToolResult.error(e.message ?: "Failed to edit file")
        }
    }
    
    private suspend fun executeDeleteFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: path")
            
            fileRepository.deleteFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing delete_file", e)
            ToolResult.error(e.message ?: "Failed to delete file")
        }
    }
    
    private suspend fun executeSearchFiles(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val query = params["query"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: query")
            val directory = params["directory"]?.jsonPrimitive?.content
            
            fileRepository.searchFiles(query, directory)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing search_files", e)
            ToolResult.error(e.message ?: "Failed to search files")
        }
    }
    
    private suspend fun executeListDirectory(arguments: String): ToolResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
            
            val result = fileRepository.listDirectory(path)
            result.fold(
                onSuccess = { files ->
                    val output = buildString {
                        appendLine("Contents of ${path ?: "current directory"}:")
                        files.forEach { file ->
                            val type = if (file.isDirectory) "[DIR]" else "[FILE]"
                            appendLine("  $type ${file.name} ${if (file.isFile) "(${formatSize(file.size)})" else ""}")
                        }
                    }
                    ToolResult.success(output)
                },
                onFailure = { error ->
                    ToolResult.error(error.message ?: "Failed to list directory")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing list_directory", e)
            ToolResult.error(e.message ?: "Failed to list directory")
        }
    }
    
    private suspend fun executeCreateDirectory(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
                ?: return ToolResult.error("Missing required parameter: path")
            
            fileRepository.createDirectory(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error executing create_directory", e)
            ToolResult.error(e.message ?: "Failed to create directory")
        }
    }
    
    private fun parseArguments(arguments: String): Map<String, kotlinx.serialization.json.JsonElement> {
        return try {
            json.parseToJsonElement(arguments).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing arguments: $arguments", e)
            emptyMap()
        }
    }
    
    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    companion object {
        private const val TAG = "ToolHandler"
    }
}
