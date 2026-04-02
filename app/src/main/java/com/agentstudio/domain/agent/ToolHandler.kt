package com.agentstudio.domain.agent

import android.util.Log
import com.agentstudio.data.api.ImageSearchApi
import com.agentstudio.data.api.ImageResult
import com.agentstudio.data.api.WebSearchApi
import com.agentstudio.data.api.WebSearchResult
import com.agentstudio.data.repository.FileRepository
import com.agentstudio.domain.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

class ToolHandler(
    private val fileRepository: FileRepository,
    private val imageSearchApi: ImageSearchApi = ImageSearchApi(),
    private val webSearchApi: WebSearchApi = WebSearchApi()
) {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    
    suspend fun executeTool(name: String, arguments: String): ToolResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "╔══════════════════════════════════════════════════════")
        Log.d(TAG, "║ TOOL EXECUTION")
        Log.d(TAG, "║ Name: $name")
        Log.d(TAG, "║ Arguments: ${arguments.take(200)}${if (arguments.length > 200) "..." else ""}")
        Log.d(TAG, "╚══════════════════════════════════════════════════════")
        
        val result = when (name) {
            // File Tools
            "create_file" -> executeCreateFile(arguments)
            "read_file" -> executeReadFile(arguments)
            "edit_file" -> executeEditFile(arguments)
            "delete_file" -> executeDeleteFile(arguments)
            "search_files" -> executeSearchFiles(arguments)
            "list_directory" -> executeListDirectory(arguments)
            "create_directory" -> executeCreateDirectory(arguments)
            
            // Web Tools
            "web_search" -> executeWebSearch(arguments)
            "wiki_search" -> executeWikiSearch(arguments)
            
            // Image Tools
            "image_search" -> executeImageSearch(arguments)
            "image_info" -> executeImageInfo(arguments)
            
            else -> {
                Log.e(TAG, "Unknown tool: $name")
                ToolResult.error("Unknown tool: $name. Available tools: create_file, read_file, edit_file, delete_file, list_directory, create_directory, search_files, web_search, wiki_search, image_search, image_info")
            }
        }
        
        Log.d(TAG, "┌──────────────────────────────────────────────────────")
        Log.d(TAG, "│ TOOL RESULT")
        Log.d(TAG, "│ Success: ${result.success}")
        Log.d(TAG, "│ Output: ${result.output.take(300)}${if (result.output.length > 300) "..." else ""}")
        if (!result.success) {
            Log.d(TAG, "│ Error: ${result.error}")
        }
        Log.d(TAG, "└──────────────────────────────────────────────────────")
        
        result
    }
    
    // ==================== FILE TOOLS ====================
    
    private suspend fun executeCreateFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            Log.d(TAG, "Parsed params: ${params.keys}")
            
            val path = params["path"]?.jsonPrimitive?.content
            if (path == null) {
                Log.e(TAG, "Missing 'path' parameter. Available: ${params.keys}")
                return ToolResult.error("Missing required parameter 'path'. Please provide the full file path.")
            }
            
            val content = params["content"]?.jsonPrimitive?.content ?: ""
            Log.d(TAG, "Creating file: $path")
            Log.d(TAG, "Content length: ${content.length} characters")
            
            fileRepository.createFile(path, content)
        } catch (e: Exception) {
            Log.e(TAG, "Error in create_file", e)
            ToolResult.error("Failed to create file: ${e.message}")
        }
    }
    
    private suspend fun executeReadFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
            if (path == null) {
                return ToolResult.error("Missing required parameter 'path'. Please provide the file path to read.")
            }
            
            Log.d(TAG, "Reading file: $path")
            fileRepository.readFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error in read_file", e)
            ToolResult.error("Failed to read file: ${e.message}")
        }
    }
    
    private suspend fun executeEditFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
            val oldContent = params["old_content"]?.jsonPrimitive?.content
            val newContent = params["new_content"]?.jsonPrimitive?.content
            
            if (path == null) return ToolResult.error("Missing parameter: path")
            if (oldContent == null) return ToolResult.error("Missing parameter: old_content")
            if (newContent == null) return ToolResult.error("Missing parameter: new_content")
            
            Log.d(TAG, "Editing file: $path")
            fileRepository.editFile(path, oldContent, newContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error in edit_file", e)
            ToolResult.error("Failed to edit file: ${e.message}")
        }
    }
    
    private suspend fun executeDeleteFile(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
            if (path == null) return ToolResult.error("Missing parameter: path")
            
            Log.d(TAG, "Deleting: $path")
            fileRepository.deleteFile(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error in delete_file", e)
            ToolResult.error("Failed to delete: ${e.message}")
        }
    }
    
    private suspend fun executeSearchFiles(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val query = params["query"]?.jsonPrimitive?.content
            if (query == null) return ToolResult.error("Missing parameter: query")
            
            val directory = params["directory"]?.jsonPrimitive?.content
            Log.d(TAG, "Searching files: $query in $directory")
            
            fileRepository.searchFiles(query, directory)
        } catch (e: Exception) {
            Log.e(TAG, "Error in search_files", e)
            ToolResult.error("Search failed: ${e.message}")
        }
    }
    
    private suspend fun executeListDirectory(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
            
            val targetPath = path ?: "/storage/emulated/0/Documents/AgentStudioProject"
            Log.d(TAG, "Listing directory: $targetPath")
            
            val result = fileRepository.listDirectory(targetPath)
            result.fold(
                onSuccess = { files ->
                    val currentDir = targetPath
                    val output = buildString {
                        appendLine("📁 Directory: $currentDir")
                        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        if (files.isEmpty()) {
                            appendLine("📂 (empty directory)")
                        } else {
                            files.forEach { file ->
                                val icon = if (file.isDirectory) "📁" else "📄"
                                val size = if (!file.isDirectory) " (${formatFileSize(file.size)})" else ""
                                appendLine("$icon ${file.name}$size")
                            }
                        }
                        appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                        appendLine("📊 Total: ${files.size} items (${files.count { it.isDirectory }} folders, ${files.count { !it.isDirectory }} files)")
                    }
                    ToolResult.success(output)
                },
                onFailure = { error ->
                    ToolResult.error("Failed to list directory: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in list_directory", e)
            ToolResult.error("Error: ${e.message}")
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
    
    private suspend fun executeCreateDirectory(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val path = params["path"]?.jsonPrimitive?.content
            if (path == null) return ToolResult.error("Missing parameter: path")
            
            Log.d(TAG, "Creating directory: $path")
            fileRepository.createDirectory(path)
        } catch (e: Exception) {
            Log.e(TAG, "Error in create_directory", e)
            ToolResult.error("Failed to create directory: ${e.message}")
        }
    }
    
    // ==================== WEB TOOLS ====================
    
    private suspend fun executeWebSearch(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val query = params["query"]?.jsonPrimitive?.content
            if (query.isNullOrBlank()) {
                return ToolResult.error("Missing parameter: query")
            }
            
            val maxResults = params["max_results"]?.jsonPrimitive?.intOrNull ?: 10
            
            Log.d(TAG, "Web search: '$query' (max: $maxResults)")
            
            val result = webSearchApi.search(query, maxResults)
            result.fold(
                onSuccess = { results ->
                    if (results.isEmpty()) {
                        ToolResult.success("🔍 No results found for: $query")
                    } else {
                        val output = buildString {
                            appendLine("🔍 Web Search Results for: \"$query\"")
                            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            appendLine("Found ${results.size} results\n")
                            results.forEachIndexed { index, r ->
                                appendLine("${index + 1}. ${r.title}")
                                appendLine("   🔗 ${r.url}")
                                appendLine("   📝 ${r.snippet.take(200)}${if (r.snippet.length > 200) "..." else ""}")
                                if (r.source.isNotEmpty()) {
                                    appendLine("   📚 Source: ${r.source}")
                                }
                                appendLine()
                            }
                        }
                        ToolResult.success(output)
                    }
                },
                onFailure = { error ->
                    ToolResult.error("Search failed: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in web_search", e)
            ToolResult.error("Search error: ${e.message}")
        }
    }
    
    private suspend fun executeWikiSearch(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val query = params["query"]?.jsonPrimitive?.content
            if (query.isNullOrBlank()) {
                return ToolResult.error("Missing parameter: query")
            }
            
            val maxResults = params["max_results"]?.jsonPrimitive?.intOrNull ?: 5
            
            Log.d(TAG, "Wiki search: '$query'")
            
            val result = webSearchApi.searchWikipedia(query, maxResults)
            result.fold(
                onSuccess = { results ->
                    if (results.isEmpty()) {
                        ToolResult.success("📚 No Wikipedia results for: $query")
                    } else {
                        val output = buildString {
                            appendLine("📚 Wikipedia Results for: \"$query\"")
                            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            results.forEachIndexed { index, r ->
                                appendLine("\n${index + 1}. ${r.title}")
                                appendLine("   🔗 ${r.url}")
                                appendLine("   📝 ${r.snippet}")
                            }
                        }
                        ToolResult.success(output)
                    }
                },
                onFailure = { error ->
                    ToolResult.error("Wikipedia search failed: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in wiki_search", e)
            ToolResult.error("Error: ${e.message}")
        }
    }
    
    // ==================== IMAGE TOOLS ====================
    
    private suspend fun executeImageSearch(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val tags = params["tags"]?.jsonPrimitive?.content
            if (tags.isNullOrBlank()) {
                return ToolResult.error("Missing parameter: tags. Please provide search tags like 'cat cute' or 'nature landscape'.")
            }
            
            val limit = (params["limit"]?.jsonPrimitive?.intOrNull ?: 10).coerceIn(1, 50)
            val rating = params["rating"]?.jsonPrimitive?.content ?: "safe"
            
            Log.d(TAG, "Image search: '$tags' (limit: $limit, rating: $rating)")
            
            val result = imageSearchApi.searchImages(tags, limit, rating)
            result.fold(
                onSuccess = { images ->
                    if (images.isEmpty()) {
                        ToolResult.success("🖼️ No images found for: $tags\nTry different keywords or check spelling.")
                    } else {
                        val output = buildString {
                            appendLine("🖼️ Image Search Results for: \"$tags\"")
                            appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                            appendLine("Found ${images.size} images from multiple sources\n")
                            images.forEachIndexed { index, img ->
                                appendLine("${index + 1}. [${img.source}] ID: ${img.id}")
                                appendLine("   🖼️ ${img.url}")
                                appendLine("   📊 ${img.width}x${img.height} | Rating: ${img.rating}")
                                if (img.tags.isNotEmpty()) {
                                    appendLine("   🏷️ ${img.getDisplayTags()}")
                                }
                                if (!img.user.isNullOrBlank()) {
                                    appendLine("   👤 ${img.user}")
                                }
                                appendLine()
                            }
                        }
                        ToolResult.success(output)
                    }
                },
                onFailure = { error ->
                    ToolResult.error("Image search failed: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in image_search", e)
            ToolResult.error("Error: ${e.message}")
        }
    }
    
    private suspend fun executeImageInfo(arguments: String): ToolResult {
        return try {
            val params = parseArguments(arguments)
            val id = params["id"]?.jsonPrimitive?.content
            if (id.isNullOrBlank()) {
                return ToolResult.error("Missing parameter: id")
            }
            
            // For now, return info about searching by ID
            ToolResult.success("🖼️ Image Info\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\nID: $id\nTo get more images, use image_search with tags.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in image_info", e)
            ToolResult.error("Error: ${e.message}")
        }
    }
    
    // ==================== HELPERS ====================
    
    private fun parseArguments(arguments: String): Map<String, kotlinx.serialization.json.JsonElement> {
        return try {
            val trimmed = arguments.trim()
            if (trimmed.isEmpty()) {
                Log.w(TAG, "Empty arguments string")
                return emptyMap()
            }
            json.parseToJsonElement(trimmed).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse arguments: '${arguments.take(100)}...'", e)
            emptyMap()
        }
    }
    
    companion object {
        private const val TAG = "ToolHandler"
    }
}
