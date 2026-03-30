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
    val enum: List<String>? = null,
    val default: String? = null
)

object AgentTools {
    
    val CREATE_FILE = AgentTool(
        name = "create_file",
        description = "Create a new file with the specified content. Use this to create new source files, configuration files, or any text-based file.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The path where the file should be created. Can be relative to the current directory or absolute."
            ),
            "content" to ToolParameter(
                type = "string",
                description = "The content to write to the file."
            )
        )
    )
    
    val READ_FILE = AgentTool(
        name = "read_file",
        description = "Read the contents of a file. Returns the file content as a string.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The path of the file to read."
            )
        )
    )
    
    val EDIT_FILE = AgentTool(
        name = "edit_file",
        description = "Edit an existing file by replacing specific content. The old_content must match exactly what's in the file.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The path of the file to edit."
            ),
            "old_content" to ToolParameter(
                type = "string",
                description = "The exact text to find and replace."
            ),
            "new_content" to ToolParameter(
                type = "string",
                description = "The new text to replace the old content with."
            )
        )
    )
    
    val DELETE_FILE = AgentTool(
        name = "delete_file",
        description = "Delete a file or directory. Directories are deleted recursively.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The path of the file or directory to delete."
            )
        )
    )
    
    val SEARCH_FILES = AgentTool(
        name = "search_files",
        description = "Search for files by name pattern in a directory and its subdirectories.",
        parameters = mapOf(
            "query" to ToolParameter(
                type = "string",
                description = "The search query to match against file names."
            ),
            "directory" to ToolParameter(
                type = "string",
                description = "The directory to search in. Defaults to current directory.",
                required = false
            )
        )
    )
    
    val LIST_DIRECTORY = AgentTool(
        name = "list_directory",
        description = "List the contents of a directory. Returns a list of files and subdirectories.",
        parameters = mapOf(
            "path" to ToolParameter(
                type = "string",
                description = "The path of the directory to list.",
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
                description = "The path of the directory to create."
            )
        )
    )
    
    val ALL_TOOLS = listOf(
        CREATE_FILE,
        READ_FILE,
        EDIT_FILE,
        DELETE_FILE,
        SEARCH_FILES,
        LIST_DIRECTORY,
        CREATE_DIRECTORY
    )
}
