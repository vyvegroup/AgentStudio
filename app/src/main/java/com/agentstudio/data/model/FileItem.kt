package com.agentstudio.data.model

import java.io.File

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val isDirectory: Boolean = file.isDirectory,
    val isFile: Boolean = file.isFile,
    val size: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val canRead: Boolean = file.canRead(),
    val canWrite: Boolean = file.canWrite(),
    val extension: String = if (file.isFile) file.extension else "",
    val children: List<FileItem> = emptyList()
) {
    val isHidden: Boolean
        get() = name.startsWith(".")
    
    val isCodeFile: Boolean
        get() = isFile && extension in setOf(
            "kt", "java", "py", "js", "ts", "jsx", "tsx",
            "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb",
            "swift", "m", "mm", "scala", "groovy", "php",
            "html", "css", "scss", "xml", "json", "yaml", "yml",
            "md", "sh", "bash", "zsh", "sql"
        )
    
    val iconRes: String
        get() = when {
            isDirectory -> "folder"
            isCodeFile -> "code"
            extension == "txt" -> "text"
            extension in setOf("png", "jpg", "jpeg", "gif", "webp", "svg") -> "image"
            extension in setOf("mp3", "wav", "ogg", "m4a") -> "audio"
            extension in setOf("mp4", "avi", "mov", "mkv") -> "video"
            extension == "pdf" -> "pdf"
            extension in setOf("zip", "tar", "gz", "rar", "7z") -> "archive"
            else -> "file"
        }
}
