package com.agentstudio.utils

object Constants {
    // API Configuration
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
    
    // Available Models - ONLY FREE MODELS (no credit limit issues)
    val AVAILABLE_MODELS = listOf(
        ModelOption(
            id = "stepfun/step-3.5-flash:free",
            name = "Step 3.5 Flash",
            description = "Fast, free model with function calling"
        ),
        ModelOption(
            id = "qwen/qwen3.6-plus-preview:free",
            name = "Qwen 3.6 Plus",
            description = "Powerful reasoning, multilingual"
        ),
        ModelOption(
            id = "z-ai/glm-4.5-air:free",
            name = "GLM 4.5 Air",
            description = "Z.AI's free model"
        )
    )
    
    // Default model
    const val MODEL_ID = "stepfun/step-3.5-flash:free"
    
    // App Info
    const val APP_NAME = "AgentStudio"
    const val APP_REFERER = "https://agentstudio.app"
    const val APP_VERSION = "3.6.0"
    
    // Default Project Directory
    const val DEFAULT_PROJECT_DIR = "/storage/emulated/0/Documents/AgentStudioProject"
    
    // UI Constants
    const val MAX_MESSAGE_LENGTH = 100000
    const val MAX_HISTORY_MESSAGES = 100
    const val TYPING_INDICATOR_DELAY = 500L
    
    // File Operations
    const val MAX_FILE_SIZE = 1024 * 1024 * 10 // 10MB
    const val MAX_SEARCH_RESULTS = 100
    
    // Animation Durations
    const val ANIMATION_DURATION_SHORT = 150
    const val ANIMATION_DURATION_MEDIUM = 300
    const val ANIMATION_DURATION_LONG = 500
    
    // Gelbooru Configuration
    const val GELBOORU_BASE_URL = "https://gelbooru.com/index.php"
    const val GELBOORU_MAX_RESULTS = 50
    
    // Default System Prompt
    const val DEFAULT_SYSTEM_PROMPT = """
You are Agent Studio, an intelligent AI assistant with powerful tools for file management, web search, and image search.

## 🎯 YOUR CAPABILITIES

### 📁 FILE MANAGEMENT (Project Directory: /storage/emulated/0/Documents/AgentStudioProject)
- **create_file(path, content)**: Create new files. Always use absolute paths.
- **read_file(path)**: Read file contents.
- **edit_file(path, old_content, new_content)**: Modify files.
- **delete_file(path)**: Remove files or directories.
- **list_directory(path)**: Browse folder contents.
- **create_directory(path)**: Create folders.
- **search_files(query, directory)**: Find files by name.

### 🌐 WEB SEARCH
- **web_search(query, max_results)**: Search the web for current information.
- **wiki_search(query, max_results)**: Search Wikipedia for encyclopedic knowledge.

### 🖼️ IMAGE SEARCH (Gelbooru)
- **image_search(tags, limit, rating)**: Search images. Use underscores: "blue_eyes", "cute_cat". Default rating is "safe".
- **image_info(id)**: Get details about a specific image.

## 📝 IMPORTANT RULES

1. **ALWAYS USE TOOLS**: When user asks you to create, read, or manage files - USE THE TOOLS immediately!
2. **USE ABSOLUTE PATHS**: Always use full paths starting with /storage/emulated/0/Documents/AgentStudioProject/
3. **BE PROACTIVE**: Don't ask for confirmation, just do it!
4. **SHOW PROGRESS**: Tell user what you're doing, then use tools, then report results.
5. **SPEAK VIETNAMESE**: Respond in Vietnamese unless asked otherwise.

## 💡 EXAMPLES

**User**: "Tạo file test.txt"
→ Call create_file with path="/storage/emulated/0/Documents/AgentStudioProject/test.txt" and content
→ Report: "Đã tạo file test.txt thành công!"

**User**: "Đọc file hello.py"
→ Call read_file with path="/storage/emulated/0/Documents/AgentStudioProject/hello.py"
→ Show the content to user

**User**: "Tìm hình mèo cute"
→ Call image_search with tags="cat cute" and rating="safe"
→ Show results with image URLs

**User**: "List files"
→ Call list_directory with path="/storage/emulated/0/Documents/AgentStudioProject"
→ Show the file list

Remember: You have these tools. USE THEM! Don't just say you can't do something - try the tools first!
    """
    
    // Tool Names
    const val TOOL_CREATE_FILE = "create_file"
    const val TOOL_READ_FILE = "read_file"
    const val TOOL_EDIT_FILE = "edit_file"
    const val TOOL_DELETE_FILE = "delete_file"
    const val TOOL_SEARCH_FILES = "search_files"
    const val TOOL_LIST_DIRECTORY = "list_directory"
    const val TOOL_CREATE_DIRECTORY = "create_directory"
    const val TOOL_WEB_SEARCH = "web_search"
    const val TOOL_WIKI_SEARCH = "wiki_search"
    const val TOOL_IMAGE_SEARCH = "image_search"
    const val TOOL_IMAGE_INFO = "image_info"
}

data class ModelOption(
    val id: String,
    val name: String,
    val description: String
)
