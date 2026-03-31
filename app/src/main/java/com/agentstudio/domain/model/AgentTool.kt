package com.agentstudio.domain.model

import com.agentstudio.data.model.FunctionDefinition
import com.agentstudio.data.model.PropertyDefinition
import com.agentstudio.data.model.ToolDefinition
import com.agentstudio.data.model.ToolParameters
import kotlinx.serialization.Serializable

@Serializable
data class AgentTool(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameter>
) {
    fun toToolDefinition(): ToolDefinition {
        return ToolDefinition(
            type = "function",
            function = FunctionDefinition(
                name = name,
                description = description,
                parameters = ToolParameters(
                    type = "object",
                    properties = parameters.mapValues { (_, param) ->
                        PropertyDefinition(
                            type = param.type,
                            description = param.description,
                            enum = param.enum
                        )
                    },
                    required = parameters.filter { it.value.required }.keys.toList()
                )
            )
        )
    }
}

@Serializable
data class ToolParameter(
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null
)

object AgentTools {
    
    // ==================== FILE TOOLS ====================
    
    val CREATE_FILE = AgentTool(
        name = "create_file",
        description = "Create a new file with the specified content. Creates parent directories if needed. Use absolute paths starting with /storage/emulated/0/Documents/AgentStudioProject/",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The full absolute path where the file should be created (e.g., /storage/emulated/0/Documents/AgentStudioProject/hello.txt)"
            ),
            "content" to ToolParameter(
                type = "string",
                description = "The content to write to the file"
            )
        )
    )
    
    val READ_FILE = AgentTool(
        name = "read_file",
        description = "Read the contents of a file and return it as a string. Use absolute paths.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The full absolute path of the file to read (e.g., /storage/emulated/0/Documents/AgentStudioProject/hello.txt)"
            )
        )
    )
    
    val EDIT_FILE = AgentTool(
        name = "edit_file",
        description = "Edit an existing file by replacing specific text. The old_content must match exactly.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The full absolute path of the file to edit"
            ),
            "old_content" to ToolParameter(
                type = "string",
                description = "The exact text to find and replace"
            ),
            "new_content" to ToolParameter(
                type = "string",
                description = "The new text to replace with"
            )
        )
    )
    
    val DELETE_FILE = AgentTool(
        name = "delete_file",
        description = "Delete a file or directory. Directories are deleted recursively.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The full absolute path of the file or directory to delete"
            )
        )
    )
    
    val SEARCH_FILES = AgentTool(
        name = "search_files",
        description = "Search for files by name pattern in a directory and its subdirectories.",
        parameters = mapOf(
            "query" to ToolParameter(
                type = "string",
                description = "The search query to match against file names"
            ),
            "directory" to ToolParameter(
                type = "string",
                description = "The directory to search in (default: /storage/emulated/0/Documents/AgentStudioProject)",
                required = false
            )
        )
    )
    
    val LIST_DIRECTORY = AgentTool(
        name = "list_directory",
        description = "List the contents of a directory, showing files and subdirectories. Returns the list of items with type indicators.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The full path of the directory to list. Default: /storage/emulated/0/Documents/AgentStudioProject",
                required = false
            )
        )
    )
    
    val CREATE_DIRECTORY = AgentTool(
        name = "create_directory",
        description = "Create a new directory. Parent directories will be created if they don't exist.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The full absolute path of the directory to create"
            )
        )
    )
    
    // ==================== WEB TOOLS ====================
    
    val WEB_SEARCH = AgentTool(
        name = "web_search",
        description = "Search the web for current information. Returns relevant search results with titles, URLs, and snippets. Use this for finding recent news, facts, or any current information.",
        parameters = mapOf(
            "query" to ToolParameter(
                type = "string",
                description = "The search query"
            ),
            "max_results" to ToolParameter(
                type = "integer",
                description = "Maximum number of results to return (default: 10)",
                required = false
            )
        )
    )
    
    val WIKI_SEARCH = AgentTool(
        name = "wiki_search",
        description = "Search Wikipedia for encyclopedia-style information. Best for factual, historical, or educational content.",
        parameters = mapOf(
            "query" to ToolParameter(
                type = "string",
                description = "The search query for Wikipedia"
            ),
            "max_results" to ToolParameter(
                type = "integer",
                description = "Maximum number of results (default: 5)",
                required = false
            )
        )
    )
    
    // ==================== IMAGE TOOLS ====================
    
    val IMAGE_SEARCH = AgentTool(
        name = "image_search",
        description = "Search for images on Gelbooru image board. Use tags to find specific images. Use underscores for multi-word tags (e.g., 'blue_eyes', 'white_hair'). Returns image URLs and metadata.",
        parameters = mapOf(
            "tags" to ToolParameter(
                type = "string",
                description = "Search tags separated by spaces (e.g., 'cat cute' or 'blue_eyes white_hair'). Use underscores for multi-word tags."
            ),
            "limit" to ToolParameter(
                type = "integer",
                description = "Number of images to return (default: 10, max: 50)",
                required = false
            ),
            "rating" to ToolParameter(
                type = "string",
                description = "Content rating filter",
                required = false,
                enum = listOf("safe", "all")
            )
        )
    )
    
    val IMAGE_INFO = AgentTool(
        name = "image_info",
        description = "Get detailed information about a specific image by ID from Gelbooru.",
        parameters = mapOf(
            "id" to ToolParameter(
                type = "integer",
                description = "The image ID from Gelbooru"
            )
        )
    )
    
    // ==================== ALL TOOLS ====================
    
    val FILE_TOOLS = listOf(
        CREATE_FILE,
        READ_FILE,
        EDIT_FILE,
        DELETE_FILE,
        SEARCH_FILES,
        LIST_DIRECTORY,
        CREATE_DIRECTORY
    )
    
    val WEB_TOOLS = listOf(
        WEB_SEARCH,
        WIKI_SEARCH
    )
    
    val IMAGE_TOOLS = listOf(
        IMAGE_SEARCH,
        IMAGE_INFO
    )
    
    val ALL_TOOLS = FILE_TOOLS + WEB_TOOLS + IMAGE_TOOLS
}
