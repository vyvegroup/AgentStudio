package com.agentstudio.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.agentstudio.ui.theme.CodeComment
import com.agentstudio.ui.theme.CodeFunction
import com.agentstudio.ui.theme.CodeKeyword
import com.agentstudio.ui.theme.CodeNumber
import com.agentstudio.ui.theme.CodeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorPanel(
    content: String,
    language: String,
    fileName: String,
    isEditable: Boolean = false,
    onContentChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var localContent by remember(content) { mutableStateOf(content) }
    var showLineNumbers by remember { mutableStateOf(true) }
    
    Column(
        modifier = modifier
            .animateContentSize()
    ) {
        // Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Code,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(
                    onClick = { showLineNumbers = !showLineNumbers },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatListNumbered,
                        contentDescription = "Số dòng",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        Divider()
        
        // Code content
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            color = Color(0xFF1E1E1E)
        ) {
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                // Line numbers
                if (showLineNumbers) {
                    Column(
                        modifier = Modifier
                            .width(48.dp)
                            .background(Color(0xFF252526))
                            .padding(vertical = 8.dp)
                    ) {
                        val lines = localContent.lines()
                        lines.indices.forEach { index ->
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFF858585),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
                
                // Code text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = highlightCode(localContent, language),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

fun highlightCode(code: String, language: String): AnnotatedString {
    return buildAnnotatedString {
        val keywords = when (language.lowercase()) {
            "kotlin" -> listOf("fun", "val", "var", "if", "else", "when", "for", "while", "class", 
                "interface", "object", "sealed", "data", "enum", "companion", "override", "private",
                "public", "protected", "internal", "import", "package", "return", "break", "continue",
                "null", "true", "false", "is", "as", "in", "typeof", "suspend", "inline", "reified")
            "java" -> listOf("public", "private", "protected", "class", "interface", "extends", 
                "implements", "static", "final", "void", "int", "long", "double", "float", "boolean",
                "char", "String", "if", "else", "for", "while", "do", "switch", "case", "default",
                "return", "break", "continue", "new", "this", "super", "null", "true", "false")
            "python" -> listOf("def", "class", "if", "elif", "else", "for", "while", "try", "except",
                "finally", "with", "as", "import", "from", "return", "yield", "lambda", "and", "or",
                "not", "in", "is", "None", "True", "False", "pass", "break", "continue", "raise",
                "async", "await")
            else -> listOf("if", "else", "for", "while", "return", "function", "var", "let", "const",
                "class", "import", "export", "true", "false", "null", "undefined")
        }
        
        val stringPattern = """"([^"\\]|\\.)*"|'([^'\\]|\\.)*'""".toRegex()
        val numberPattern = """\b\d+\.?\d*\b""".toRegex()
        val commentPattern = """//.*$|#.*$|/\*[\s\S]*?\*/""".toRegex(RegexOption.MULTILINE)
        val functionPattern = """\b([a-zA-Z_][a-zA-Z0-9_]*)\s*\(""".toRegex()
        
        var currentIndex = 0
        val processed = code
        
        while (currentIndex < processed.length) {
            val remainingText = processed.substring(currentIndex)
            
            // Check for comments first
            val commentMatch = commentPattern.find(remainingText)
            if (commentMatch != null && commentMatch.range.first == 0) {
                withStyle(SpanStyle(color = CodeComment)) {
                    append(commentMatch.value)
                }
                currentIndex += commentMatch.value.length
                continue
            }
            
            // Check for strings
            val stringMatch = stringPattern.find(remainingText)
            if (stringMatch != null && stringMatch.range.first == 0) {
                withStyle(SpanStyle(color = CodeString)) {
                    append(stringMatch.value)
                }
                currentIndex += stringMatch.value.length
                continue
            }
            
            // Check for keywords
            var keywordFound = false
            for (keyword in keywords) {
                val keywordPattern = """\b$keyword\b""".toRegex()
                val keywordMatch = keywordPattern.find(remainingText)
                if (keywordMatch != null && keywordMatch.range.first == 0) {
                    withStyle(SpanStyle(color = CodeKeyword, fontWeight = FontWeight.Bold)) {
                        append(keywordMatch.value)
                    }
                    currentIndex += keywordMatch.value.length
                    keywordFound = true
                    break
                }
            }
            if (keywordFound) continue
            
            // Check for numbers
            val numberMatch = numberPattern.find(remainingText)
            if (numberMatch != null && numberMatch.range.first == 0) {
                withStyle(SpanStyle(color = CodeNumber)) {
                    append(numberMatch.value)
                }
                currentIndex += numberMatch.value.length
                continue
            }
            
            // Check for function calls
            val functionMatch = functionPattern.find(remainingText)
            if (functionMatch != null && functionMatch.range.first == 0) {
                withStyle(SpanStyle(color = CodeFunction)) {
                    append(functionMatch.groupValues[1])
                }
                append("(")
                currentIndex += functionMatch.value.length
                continue
            }
            
            // Default: just append the character
            append(remainingText.first())
            currentIndex++
        }
    }
}

@Composable
fun CodeEditorPreview(
    modifier: Modifier = Modifier
) {
    val sampleCode = """
        fun main() {
            println("Hello, World!")
            
            // This is a comment
            val numbers = listOf(1, 2, 3, 4, 5)
            numbers.forEach { number ->
                println("Number: $${"$"}number")
            }
        }
    """.trimIndent()
    
    CodeEditorPanel(
        content = sampleCode,
        language = "kotlin",
        fileName = "Main.kt",
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    )
}
