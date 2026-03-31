package com.agentstudio.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.agentstudio.data.model.AgentMessage
import com.agentstudio.ui.theme.*
import com.agentstudio.ui.viewmodel.AgentViewModel
import com.agentstudio.ui.viewmodel.AgentViewModelFactory
import com.agentstudio.utils.Constants
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun AgentScreen(
    viewModel: AgentViewModel = viewModel(
        factory = AgentViewModelFactory(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val messages by viewModel.messages.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val error by viewModel.error.collectAsState()
    val debugLog by viewModel.debugLog.collectAsState()
    val showDebug by viewModel.showDebug.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // States
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    var showModelSelector by remember { mutableStateOf(false) }
    var showSkillsPanel by remember { mutableStateOf(false) }
    
    // Animated gradient offset
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset"
    )
    
    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Image Preview Dialog
    if (previewImageUrl != null) {
        ImagePreviewDialog(
            imageUrl = previewImageUrl!!,
            onDismiss = { previewImageUrl = null }
        )
    }
    
    // Model Selector Dialog
    if (showModelSelector) {
        ModelSelectorDialog(
            currentModel = currentModel,
            models = Constants.AVAILABLE_MODELS,
            onSelect = { modelId ->
                viewModel.setModel(modelId)
                showModelSelector = false
            },
            onDismiss = { showModelSelector = false }
        )
    }
    
    Scaffold(
        containerColor = Background,
        topBar = {
            Column {
                ModernTopBar(
                    isProcessing = isProcessing,
                    showDebug = showDebug,
                    currentModel = currentModel,
                    onToggleDebug = { viewModel.toggleDebug() },
                    onClearChat = { viewModel.clearChat() },
                    onSelectModel = { showModelSelector = true },
                    onToggleSkills = { showSkillsPanel = !showSkillsPanel },
                    animatedOffset = animatedOffset
                )
                
                // Debug panel
                AnimatedVisibility(
                    visible = showDebug,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    ModernDebugPanel(
                        debugLog = debugLog,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Debug", debugLog))
                            Toast.makeText(context, "Đã copy", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                
                // Skills Panel
                AnimatedVisibility(
                    visible = showSkillsPanel,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    SkillsQuickPanel(
                        onSkillClick = { skill ->
                            inputText = skill
                            showSkillsPanel = false
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Background)
        ) {
            // Messages
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Welcome message if empty
                if (messages.isEmpty()) {
                    item {
                        WelcomeMessage(
                            onQuickAction = { action ->
                                viewModel.sendMessage(action)
                            }
                        )
                    }
                }
                
                items(messages, key = { it.id }) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = slideInVertically(
                            initialOffsetY = { it / 2 }
                        ) + fadeIn(tween(300)),
                        exit = fadeOut()
                    ) {
                        ModernMessageBubble(
                            message = message,
                            onCopy = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Message", text))
                                Toast.makeText(context, "Đã copy", Toast.LENGTH_SHORT).show()
                            },
                            onImageClick = { url -> previewImageUrl = url }
                        )
                    }
                }
                
                // Typing indicator when processing
                if (isProcessing && (messages.isEmpty() || messages.last() is AgentMessage.UserMessage)) {
                    item {
                        ModernTypingIndicator()
                    }
                }
            }
            
            // Error Banner
            AnimatedVisibility(
                visible = error != null,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                error?.let { errorMsg ->
                    ModernErrorBanner(
                        error = errorMsg,
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Error", errorMsg))
                            Toast.makeText(context, "Đã copy lỗi", Toast.LENGTH_SHORT).show()
                        },
                        onRetry = { viewModel.retry() },
                        onDismiss = { viewModel.clearError() }
                    )
                }
            }
            
            // Modern Input Area
            ModernInputArea(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText.trim())
                        inputText = ""
                    }
                },
                isProcessing = isProcessing,
                onCancel = { viewModel.cancelExecution() },
                onAttach = { /* TODO: File attachment */ }
            )
        }
    }
}

@Composable
fun ModernTopBar(
    isProcessing: Boolean,
    showDebug: Boolean,
    currentModel: String,
    onToggleDebug: () -> Unit,
    onClearChat: () -> Unit,
    onSelectModel: () -> Unit,
    onToggleSkills: () -> Unit,
    animatedOffset: Float
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated AI Icon with gradient
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(GradientPurple, GradientCyan, GradientPink),
                            start = Offset(animatedOffset - 500, 0f),
                            end = Offset(animatedOffset, 500f)
                        ),
                        CircleShape
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Agent Studio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = OnBackground
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isProcessing) Success else OnBackgroundMuted,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isProcessing) "Processing..." else Constants.AVAILABLE_MODELS.find { it.id == currentModel }?.name ?: "AI Ready",
                        fontSize = 11.sp,
                        color = OnBackgroundMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            
            // Status indicator
            if (isProcessing) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        PulsingDot()
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Live",
                            fontSize = 11.sp,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            // Action buttons
            IconButton(
                onClick = onToggleSkills,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Filled.AutoFixHigh,
                    contentDescription = "Skills",
                    tint = if (showDebug) Primary else OnBackgroundMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            IconButton(
                onClick = onSelectModel,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Model",
                    tint = OnBackgroundMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            IconButton(
                onClick = onToggleDebug,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (showDebug) Primary.copy(alpha = 0.15f) else Color.Transparent,
                        CircleShape
                    )
            ) {
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = "Debug",
                    tint = if (showDebug) Primary else OnBackgroundMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            IconButton(
                onClick = onClearChat,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = "Clear",
                    tint = OnBackgroundMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .size(7.dp)
            .scale(scale)
            .background(Primary.copy(alpha = alpha), CircleShape)
    )
}

@Composable
fun ModernDebugPanel(
    debugLog: String,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0D1117)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Terminal,
                    contentDescription = null,
                    tint = Color(0xFF58A6FF),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Debug Console",
                    color = Color(0xFF58A6FF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCopy) {
                    Icon(
                        Icons.Filled.CopyAll,
                        contentDescription = "Copy",
                        tint = Color(0xFF58A6FF),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", color = Color(0xFF58A6FF), fontSize = 11.sp)
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val scrollState = rememberScrollState()
                Text(
                    debugLog.ifEmpty { "No debug output yet..." },
                    color = Color(0xFF7EE787),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    modifier = Modifier
                        .verticalScroll(scrollState)
                        .fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun SkillsQuickPanel(
    onSkillClick: (String) -> Unit
) {
    val skills = listOf(
        "📁 Files" to "List all files in project",
        "📝 Create" to "Create a new file",
        "🔍 Search" to "Search the web for",
        "🖼️ Images" to "Search images with tags",
        "📚 Wiki" to "Search Wikipedia for"
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface.copy(alpha = 0.95f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                "Quick Skills",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnBackgroundMuted,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(skills) { (icon, prompt) ->
                    SkillChip(
                        icon = icon,
                        onClick = { onSkillClick(prompt) }
                    )
                }
            }
        }
    }
}

@Composable
fun SkillChip(
    icon: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Text(
            icon,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 13.sp
        )
    }
}

@Composable
fun WelcomeMessage(
    onQuickAction: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Animated icon
        val infiniteTransition = rememberInfiniteTransition(label = "welcome")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.3f),
                            GradientCyan.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Xin chào! Tôi là Agent Studio",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = OnBackground
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "Tôi có thể giúp bạn quản lý file, tìm kiếm web, và tìm hình ảnh.",
            fontSize = 14.sp,
            color = OnBackgroundMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Quick actions
        val quickActions = listOf(
            "📁 List files" to "List all files in my project",
            "🔍 Web search" to "Search the web for latest AI news",
            "🖼️ Find images" to "Search images with tags cute cat"
        )
        
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickActions.forEach { (label, prompt) ->
                Surface(
                    onClick = { onQuickAction(prompt) },
                    shape = RoundedCornerShape(12.dp),
                    color = CardBackground.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(label, fontSize = 14.sp, color = OnBackground)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = OnBackgroundMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModernMessageBubble(
    message: AgentMessage,
    onCopy: (String) -> Unit,
    onImageClick: (String) -> Unit
) {
    val isUser = message is AgentMessage.UserMessage
    val isTool = message is AgentMessage.ToolMessage
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Message header with icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        ) {
            val (icon, label, color) = when {
                isUser -> Triple(Icons.Filled.Person, "You", Primary)
                isTool -> {
                    val toolMsg = message as AgentMessage.ToolMessage
                    val toolIcon = when {
                        toolMsg.toolName.contains("file", ignoreCase = true) -> Icons.Filled.Folder
                        toolMsg.toolName.contains("search", ignoreCase = true) -> Icons.Filled.Search
                        toolMsg.toolName.contains("image", ignoreCase = true) -> Icons.Filled.Image
                        toolMsg.toolName.contains("wiki", ignoreCase = true) -> Icons.Filled.MenuBook
                        toolMsg.toolName.contains("web", ignoreCase = true) -> Icons.Filled.Language
                        else -> Icons.Filled.Build
                    }
                    Triple(toolIcon, toolMsg.toolName.replace("_", " ").capitalize(), if (toolMsg.isSuccess) Success else Error)
                }
                else -> Triple(Icons.Filled.SmartToy, "AI", GradientCyan)
            }
            
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(12.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            
            if (message is AgentMessage.AgentResponse && message.isStreaming) {
                Spacer(modifier = Modifier.width(6.dp))
                PulsingDot()
            }
        }
        
        // Message card with modern design
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .animateContentSize(),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = when {
                isUser -> Primary.copy(alpha = 0.12f)
                isTool -> {
                    val toolMsg = message as AgentMessage.ToolMessage
                    if (toolMsg.isSuccess) Success.copy(alpha = 0.08f)
                    else Error.copy(alpha = 0.08f)
                }
                else -> CardBackground
            },
            border = if (!isUser && !isTool) {
                androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.04f))
            } else null
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Content
                val content = when (message) {
                    is AgentMessage.UserMessage -> message.content
                    is AgentMessage.AgentResponse -> message.content.ifEmpty { "..." }
                    is AgentMessage.ToolMessage -> formatToolResult(message)
                    is AgentMessage.SystemMessage -> message.content
                }
                
                SelectionContainer {
                    Text(
                        text = formatMessageContent(content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isUser -> OnBackground
                            isTool -> {
                                val toolMsg = message as AgentMessage.ToolMessage
                                if (toolMsg.isSuccess) Success else Error
                            }
                            else -> OnBackground.copy(alpha = 0.92f)
                        },
                        lineHeight = 20.sp,
                        fontSize = 14.sp
                    )
                }
                
                // Extract and show images from tool results
                if (isTool) {
                    val toolMsg = message as AgentMessage.ToolMessage
                    val imageUrls = extractImageUrls(toolMsg.result)
                    if (imageUrls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ImageGrid(
                            imageUrls = imageUrls.take(4),
                            onImageClick = onImageClick
                        )
                    }
                }
                
                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    SmallActionButton(
                        icon = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        onClick = { onCopy(content) }
                    )
                }
            }
        }
    }
}

@Composable
fun formatMessageContent(content: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = content.lines()
        var inCodeBlock = false
        
        lines.forEachIndexed { index, line ->
            if (line.startsWith("```")) {
                inCodeBlock = !inCodeBlock
                if (inCodeBlock) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = CodeFunction)) {
                        append(line.trimStart('`'))
                    }
                }
                append("\n")
            } else if (inCodeBlock) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Color(0xFFE6EDF3))) {
                    append(line)
                }
                append("\n")
            } else {
                // Check for bold
                val boldRegex = Regex("\\*\\*(.+?)\\*\\*")
                val lastIndex = boldRegex.findAll(line).fold(0) { acc, match ->
                    append(line.substring(acc, match.range.first))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Primary)) {
                        append(match.groupValues[1])
                    }
                    match.range.last + 1
                }
                append(line.substring(lastIndex))
                
                if (index < lines.lastIndex) append("\n")
            }
        }
    }
}

@Composable
fun formatToolResult(message: AgentMessage.ToolMessage): String {
    val sb = StringBuilder()
    sb.append(message.result)
    return sb.toString()
}

fun extractImageUrls(result: String): List<String> {
    val urls = mutableListOf<String>()
    val urlRegex = Regex("""https?://[^\s<>"{}|\\^`\[\]]+\.(jpg|jpeg|png|gif|webp)""", RegexOption.IGNORE_CASE)
    urlRegex.findAll(result).forEach { match ->
        urls.add(match.value)
    }
    return urls
}

@Composable
fun ImageGrid(
    imageUrls: List<String>,
    onImageClick: (String) -> Unit
) {
    val rows = imageUrls.chunked(2)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Image",
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onImageClick(url) },
                        contentScale = ContentScale.Crop
                    )
                }
                if (row.size < 2) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ImagePreviewDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Fit
            )
            
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun ModelSelectorDialog(
    currentModel: String,
    models: List<com.agentstudio.utils.ModelOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    "Select Model",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = OnBackground
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(models) { model ->
                        val isSelected = model.id == currentModel
                        Surface(
                            onClick = { onSelect(model.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Primary.copy(alpha = 0.15f) else Surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Primary else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        model.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Primary else OnBackground
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = Primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    model.description,
                                    fontSize = 12.sp,
                                    color = OnBackgroundMuted,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernTypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBackground
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val offset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = -6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(300, delayMillis = index * 100),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .offset(y = offset.dp)
                            .size(8.dp)
                            .background(
                                when (index) {
                                    0 -> Primary
                                    1 -> GradientCyan
                                    else -> Tertiary
                                },
                                CircleShape
                            )
                    )
                    if (index < 2) Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }
    }
}

@Composable
fun ModernErrorBanner(
    error: String,
    onCopy: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Error.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Error,
                contentDescription = "Error",
                tint = Error,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = Error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                fontSize = 12.sp
            )
            
            SmallActionButton(Icons.Outlined.ContentCopy, "Copy", onCopy)
            SmallActionButton(Icons.Outlined.Refresh, "Retry", onRetry)
            SmallActionButton(Icons.Outlined.Close, "Dismiss", onDismiss)
        }
    }
}

@Composable
fun SmallActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = OnBackgroundMuted
        )
    }
}

@Composable
fun ModernInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isProcessing: Boolean,
    onCancel: () -> Unit,
    onAttach: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Surface.copy(alpha = 0.98f),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attach button
            IconButton(
                onClick = onAttach,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = "Attach",
                    tint = OnBackgroundMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        "Nhập tin nhắn...",
                        color = OnBackgroundMuted,
                        fontSize = 14.sp
                    )
                },
                maxLines = 5,
                enabled = !isProcessing,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    focusedContainerColor = CardBackground,
                    unfocusedContainerColor = CardBackground.copy(alpha = 0.5f),
                    cursorColor = Primary,
                    focusedTextColor = OnBackground,
                    unfocusedTextColor = OnBackground
                )
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Send button
            FilledIconButton(
                onClick = onSend,
                enabled = text.isNotBlank() && !isProcessing,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.25f)
                )
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = OnPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = OnPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            // Cancel button
            AnimatedVisibility(
                visible = isProcessing,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                Row {
                    Spacer(modifier = Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Error
                        )
                    ) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
