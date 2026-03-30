package com.agentstudio.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    
    private val sizeFormat = DecimalFormat("#,##0.#")
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${sizeFormat.format(bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${sizeFormat.format(bytes / (1024.0 * 1024))} MB"
            else -> "${sizeFormat.format(bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }
    
    fun formatDate(timestamp: Long): String {
        return dateFormat.format(Date(timestamp))
    }
    
    fun getExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot > 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1).lowercase()
        } else {
            ""
        }
    }
    
    fun getFileName(path: String): String {
        val lastSeparator = path.lastIndexOf(File.separatorChar)
        return if (lastSeparator >= 0 && lastSeparator < path.length - 1) {
            path.substring(lastSeparator + 1)
        } else {
            path
        }
    }
    
    fun getParentPath(path: String): String? {
        val lastSeparator = path.lastIndexOf(File.separatorChar)
        return if (lastSeparator > 0) {
            path.substring(0, lastSeparator)
        } else {
            null
        }
    }
    
    fun isCodeFile(fileName: String): Boolean {
        val ext = getExtension(fileName)
        return ext in setOf(
            "kt", "java", "py", "js", "ts", "jsx", "tsx",
            "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb",
            "swift", "m", "mm", "scala", "groovy", "php",
            "html", "css", "scss", "xml", "json", "yaml", "yml",
            "md", "sh", "bash", "zsh", "sql"
        )
    }
    
    fun isTextFile(fileName: String): Boolean {
        val ext = getExtension(fileName)
        return ext in setOf(
            "txt", "log", "cfg", "conf", "ini", "properties",
            "md", "rst", "tex"
        ) || isCodeFile(fileName)
    }
    
    fun isImageFile(fileName: String): Boolean {
        val ext = getExtension(fileName)
        return ext in setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp")
    }
    
    fun isBinaryFile(fileName: String): Boolean {
        val ext = getExtension(fileName)
        return ext in setOf(
            "apk", "dex", "class", "jar", "aar",
            "zip", "tar", "gz", "rar", "7z",
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "mp3", "mp4", "avi", "mov", "mkv", "wav", "flac"
        )
    }
    
    fun getFileIcon(fileName: String): String {
        val ext = getExtension(fileName)
        return when {
            ext in setOf("kt", "java") -> "kotlin"
            ext == "py" -> "python"
            ext in setOf("js", "jsx") -> "javascript"
            ext in setOf("ts", "tsx") -> "typescript"
            ext in setOf("html", "htm") -> "html"
            ext in setOf("css", "scss") -> "css"
            ext == "json" -> "json"
            ext == "xml" -> "xml"
            ext == "md" -> "markdown"
            ext in setOf("png", "jpg", "jpeg", "gif", "webp") -> "image"
            ext == "pdf" -> "pdf"
            ext in setOf("zip", "tar", "gz", "rar", "7z") -> "archive"
            else -> "file"
        }
    }
    
    fun getLanguageForExtension(ext: String): String {
        return when (ext.lowercase()) {
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "py" -> "python"
            "js" -> "javascript"
            "ts" -> "typescript"
            "jsx" -> "jsx"
            "tsx" -> "tsx"
            "c" -> "c"
            "cpp", "hpp" -> "cpp"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "rb" -> "ruby"
            "swift" -> "swift"
            "php" -> "php"
            "html", "htm" -> "html"
            "css" -> "css"
            "scss" -> "scss"
            "xml" -> "xml"
            "json" -> "json"
            "yaml", "yml" -> "yaml"
            "md" -> "markdown"
            "sh", "bash" -> "bash"
            "sql" -> "sql"
            else -> "text"
        }
    }
    
    fun copyFile(source: File, dest: File): Boolean {
        return try {
            FileInputStream(source).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
    
    fun moveFile(source: File, dest: File): Boolean {
        return if (source.renameTo(dest)) {
            true
        } else {
            copyFile(source, dest) && source.delete()
        }
    }
    
    fun getUniqueFileName(directory: File, baseName: String, extension: String): String {
        var name = if (extension.isNotEmpty()) "$baseName.$extension" else baseName
        var file = File(directory, name)
        var counter = 1
        
        while (file.exists()) {
            name = if (extension.isNotEmpty()) {
                "${baseName}_$counter.$extension"
            } else {
                "${baseName}_$counter"
            }
            file = File(directory, name)
            counter++
        }
        
        return name
    }
    
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        
        if (fileName == null) {
            fileName = uri.path?.let { getFileName(it) }
        }
        
        return fileName
    }
    
    fun readFileFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }
    
    fun countLines(text: String): Int {
        return text.count { it == '\n' } + 1
    }
    
    fun truncateText(text: String, maxLength: Int): String {
        return if (text.length <= maxLength) {
            text
        } else {
            text.take(maxLength - 3) + "..."
        }
    }
}
