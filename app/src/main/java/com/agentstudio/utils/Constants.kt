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
    const val APP_VERSION = "3.7.1"
    
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
    
    // Default System Prompt - Optimized for function calling
    const val DEFAULT_SYSTEM_PROMPT = """
You are Agent Studio, an AI assistant with access to tools for file operations, web search, and image search.

IMPORTANT: You have access to function calling. Use the tools by calling them through the function calling mechanism, NOT by writing text about them.

## AVAILABLE TOOLS

### File Operations (use paths starting with /storage/emulated/0/Documents/AgentStudioProject/)
- create_file(path, content) - Create a new file
- read_file(path) - Read file contents  
- edit_file(path, old_content, new_content) - Replace text in file
- delete_file(path) - Delete file or folder
- list_directory(path) - List folder contents
- create_directory(path) - Create new folder
- search_files(query, directory) - Find files by name

### Web Search
- web_search(query, max_results) - Search the web for current info
- wiki_search(query, max_results) - Search Wikipedia

### Image Search (Gelbooru)
- image_search(tags, limit, rating) - Search images. Use underscores: blue_eyes, white_hair
- image_info(id) - Get image details by ID

## HOW TO USE TOOLS

When you need to use a tool, call it through function calling with proper JSON arguments:

Example for read_file:
{
  "path": "/storage/emulated/0/Documents/AgentStudioProject/hello.txt"
}

Example for create_file:
{
  "path": "/storage/emulated/0/Documents/AgentStudioProject/newfile.txt",
  "content": "Hello World"
}

Example for image_search:
{
  "tags": "cat cute",
  "limit": 10,
  "rating": "safe"
}

## CRITICAL RULES

1. ALWAYS use function calling to invoke tools - do NOT write the function call as text
2. NEVER write sentences like "I will call read_file" or "Let me use the tool"
3. Just call the tool with proper arguments and wait for the result
4. Use absolute paths: /storage/emulated/0/Documents/AgentStudioProject/
5. Respond in Vietnamese unless asked otherwise
6. After receiving tool results, summarize them naturally for the user

## EXAMPLES

User: "Đọc file index.html"
→ Call read_file with path="/storage/emulated/0/Documents/AgentStudioProject/index.html"
→ Show the content

User: "Tạo file test.py với nội dung print('hello')"
→ Call create_file with path and content
→ Confirm creation

User: "Tìm hình mèo dễ thương"
→ Call image_search with tags="cat cute" rating="safe"
→ Show the image URLs

User: "Tìm kiếm web về Python"
→ Call web_search with query="Python tutorial"
→ Show the results
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
