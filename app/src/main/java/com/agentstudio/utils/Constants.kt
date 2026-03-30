package com.agentstudio.utils

object Constants {
    // API Configuration
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
    const val MODEL_ID = "nvidia/nemotron-3-super-120b-a12b:free"
    
    // App Info
    const val APP_NAME = "AgentStudio"
    const val APP_REFERER = "https://agentstudio.app"
    const val APP_VERSION = "1.0.0"
    
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
    
    // Default System Prompt
    const val DEFAULT_SYSTEM_PROMPT = """
        Bạn là một AI assistant thông minh và hữu ích. Bạn có thể:
        - Trả lời câu hỏi và hỗ trợ người dùng
        - Tạo, đọc, sửa và xóa file
        - Tìm kiếm và quản lý thư mục
        - Viết và debug code
        
        Hãy luôn:
        - Trả lời bằng tiếng Việt
        - Giải thích rõ ràng và chi tiết
        - Hỏi lại nếu cần thêm thông tin
        - Thông báo trước khi thực hiện các thao tác có rủi ro
    """
    
    // Tool Names
    const val TOOL_CREATE_FILE = "create_file"
    const val TOOL_READ_FILE = "read_file"
    const val TOOL_EDIT_FILE = "edit_file"
    const val TOOL_DELETE_FILE = "delete_file"
    const val TOOL_SEARCH_FILES = "search_files"
    const val TOOL_LIST_DIRECTORY = "list_directory"
    const val TOOL_CREATE_DIRECTORY = "create_directory"
}
