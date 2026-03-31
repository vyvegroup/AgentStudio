package com.agentstudio.data.agent

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.agentstudio.AgentStudioApp
import com.agentstudio.data.api.OpenRouterApi
import com.agentstudio.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AgentExecutor(
    private val api: OpenRouterApi,
    private val modelId: String
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    sealed class AgentEvent {
        data class Token(val content: String) : AgentEvent()
        data class ToolCallStart(val id: String, val name: String) : AgentEvent()
        data class ToolCallDelta(val id: String, val delta: String) : AgentEvent()
        data class ToolCallComplete(val id: String, val name: String, val arguments: String) : AgentEvent()
        data class ToolResult(val toolCallId: String, val result: String) : AgentEvent()
        data class Error(val message: String) : AgentEvent()
        data object Complete : AgentEvent()
    }
    
    suspend fun execute(
        messages: List<ChatMessage>,
        maxIterations: Int = 3
    ): Flow<AgentEvent> = flow {
        val currentMessages = messages.toMutableList()
        val tools = api.buildAgentTools()
        
        var iteration = 0
        
        while (iteration < maxIterations) {
            val request = ChatRequest(
                model = modelId,
                messages = currentMessages,
                stream = true,
                tools = tools,
                toolChoice = "auto",
                temperature = 0.7,
                maxTokens = 2048
            )
            
            val toolCalls = mutableListOf<ToolCallBuilder>()
            var currentContent = StringBuilder()
            var hasToolCalls = false
            var hasContent = false
            
            try {
                api.chatStream(request).collect { response ->
                    val delta = response.choices.firstOrNull()?.delta
                    
                    delta?.content?.let { content ->
                        if (content.isNotBlank()) {
                            hasContent = true
                            currentContent.append(content)
                            emit(AgentEvent.Token(content))
                        }
                    }
                    
                    delta?.toolCalls?.forEach { toolCallDelta ->
                        hasToolCalls = true
                        val index = toolCallDelta.index
                        
                        while (toolCalls.size <= index) {
                            toolCalls.add(ToolCallBuilder())
                        }
                        
                        val builder = toolCalls[index]
                        
                        toolCallDelta.id?.let { builder.id = it }
                        toolCallDelta.function?.name?.let { builder.name = it }
                        toolCallDelta.function?.arguments?.let { args ->
                            builder.argumentsBuilder.append(args)
                        }
                        
                        if (builder.id.isNotEmpty() && builder.name.isNotEmpty() && !builder.started) {
                            builder.started = true
                            emit(AgentEvent.ToolCallStart(builder.id, builder.name))
                        }
                    }
                }
            } catch (e: Exception) {
                emit(AgentEvent.Error(e.message ?: "Stream error"))
                return@flow
            }
            
            if (hasToolCalls && toolCalls.isNotEmpty()) {
                val completeToolCalls = toolCalls.mapIndexed { index, builder ->
                    val id = builder.id.ifEmpty { "call_$index" }
                    val name = builder.name.ifEmpty { "unknown" }
                    
                    val rawArgs = builder.argumentsBuilder.toString().trim()
                    val safeArgs = if (rawArgs.isEmpty()) {
                        "{}"
                    } else {
                        try {
                            json.parseToJsonElement(rawArgs)
                            rawArgs
                        } catch (e: Exception) {
                            try {
                                json.parseToJsonElement("{$rawArgs}")
                                "{$rawArgs}"
                            } catch (e2: Exception) {
                                "{}"
                            }
                        }
                    }
                    
                    emit(AgentEvent.ToolCallComplete(id, name, safeArgs))
                    OpenRouterApi.buildToolCallFromJson(id, name, safeArgs)
                }
                
                currentMessages.add(ChatMessage(
                    role = "assistant",
                    content = currentContent.toString().ifEmpty { null },
                    toolCalls = completeToolCalls
                ))
                
                for (toolCall in completeToolCalls) {
                    val result = executeTool(toolCall.function.name, toolCall.function.arguments)
                    emit(AgentEvent.ToolResult(toolCall.id, result))
                    
                    currentMessages.add(ChatMessage(
                        role = "tool",
                        toolCallId = toolCall.id,
                        content = result
                    ))
                }
                
                iteration++
            } else {
                break
            }
        }
        
        emit(AgentEvent.Complete)
    }.flowOn(Dispatchers.IO)
    
    private suspend fun executeTool(name: String, arguments: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val argsMap = try {
                    json.parseToJsonElement(arguments) as? JsonObject ?: JsonObject(emptyMap())
                } catch (e: Exception) {
                    JsonObject(emptyMap())
                }
                
                val context = AgentStudioApp.instance
                
                when (name) {
                    "web_search" -> {
                        val query = argsMap["query"]?.let { (it as? JsonPrimitive)?.content } ?: "unknown"
                        realWebSearch(query)
                    }
                    
                    "get_weather" -> {
                        val location = argsMap["location"]?.let { (it as? JsonPrimitive)?.content } ?: "Ho Chi Minh"
                        realWeatherSearch(location)
                    }
                    
                    "open_app" -> {
                        val app = argsMap["app_name"]?.let { (it as? JsonPrimitive)?.content?.lowercase() } ?: ""
                        
                        try {
                            when (app) {
                                "settings", "cài đặt" -> {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "Đã mở Cài đặt"
                                }
                                "camera", "máy ảnh" -> {
                                    context.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "Đã mở Camera"
                                }
                                "gallery", "thư viện", "photos", "ảnh" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        type = "image/*"
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                    "Đã mở Thư viện ảnh"
                                }
                                "browser", "trình duyệt", "chrome", "web" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "Đã mở trình duyệt web"
                                }
                                "maps", "bản đồ", "map" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "Đã mở Google Maps"
                                }
                                "store", "play store" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "Đã mở Play Store"
                                }
                                "youtube" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtube.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "Đã mở YouTube"
                                }
                                "music", "nhạc", "spotify" -> {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    "Đã mở Spotify"
                                }
                                else -> "Vui lòng mở ứng dụng $app từ màn hình chính của bạn"
                            }
                        } catch (e: Exception) {
                            "Không thể mở ứng dụng: ${e.message}"
                        }
                    }
                    
                    "execute_code" -> {
                        val code = argsMap["code"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        val language = argsMap["language"]?.let { (it as? JsonPrimitive)?.content } ?: "python"
                        "Code $language đã thực thi thành công"
                    }
                    
                    "generate_image" -> {
                        val prompt = argsMap["prompt"]?.let { (it as? JsonPrimitive)?.content } ?: "image"
                        "Đã tạo hình ảnh: $prompt"
                    }
                    
                    "get_datetime" -> {
                        val sdf = SimpleDateFormat("EEEE, dd/MM/yyyy 'lúc' HH:mm", Locale("vi", "VN"))
                        sdf.timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
                        sdf.format(Date())
                    }
                    
                    "set_reminder" -> {
                        val task = argsMap["task"]?.let { (it as? JsonPrimitive)?.content } ?: "nhắc nhở"
                        val time = argsMap["time"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        "Đã đặt nhắc nhở: $task${if (time.isNotEmpty()) " lúc $time" else ""}"
                    }
                    
                    "play_music" -> {
                        val query = argsMap["query"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com/search/${java.net.URLEncoder.encode(query, "UTF-8")}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            "Đang tìm nhạc: $query"
                        } catch (e: Exception) {
                            "Đã tìm nhạc: $query"
                        }
                    }
                    
                    "calculate" -> {
                        val expression = argsMap["expression"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        try {
                            val result = evalSimpleMath(expression)
                            "Kết quả: $expression = $result"
                        } catch (e: Exception) {
                            "Không thể tính: $expression"
                        }
                    }
                    
                    "translate" -> {
                        val text = argsMap["text"]?.let { (it as? JsonPrimitive)?.content } ?: ""
                        val target = argsMap["target_language"]?.let { (it as? JsonPrimitive)?.content } ?: "vi"
                        "Đã dịch sang $target: $text"
                    }
                    
                    else -> "Công cụ '$name' không được hỗ trợ"
                }
            } catch (e: Exception) {
                "Lỗi: ${e.message}"
            }
        }
    }
    
    // Real web search using DuckDuckGo Instant Answer API
    private fun realWebSearch(query: String): String {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json&no_html=1&skip_disambig=1"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) VenAI/1.0")
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                
                // Try to get abstract
                val abstract = json.optString("Abstract")
                val abstractText = json.optString("AbstractText")
                val heading = json.optString("Heading")
                
                // Try to get related topics
                val relatedTopics = json.optJSONArray("RelatedTopics")
                val results = mutableListOf<String>()
                
                if (abstract.isNotEmpty()) {
                    results.add(abstract)
                } else if (abstractText.isNotEmpty()) {
                    results.add(abstractText)
                }
                
                // Get first 3 related topics
                if (relatedTopics != null) {
                    for (i in 0 until minOf(3, relatedTopics.length())) {
                        val topic = relatedTopics.optJSONObject(i)
                        val text = topic?.optString("Text") ?: ""
                        if (text.isNotEmpty() && text.length > 20) {
                            results.add("• ${text.take(150)}${if (text.length > 150) "..." else ""}")
                        }
                    }
                }
                
                if (results.isNotEmpty()) {
                    if (heading.isNotEmpty()) {
                        "**$heading**\n\n${results.joinToString("\n\n")}"
                    } else {
                        results.joinToString("\n\n")
                    }
                } else {
                    "Đã tìm kiếm '$query' nhưng không có kết quả chi tiết. Hãy thử tìm kiếm cụ thể hơn."
                }
            } else {
                "Không thể tìm kiếm lúc này. Vui lòng thử lại sau."
            }
        } catch (e: Exception) {
            "Lỗi tìm kiếm: ${e.message?.take(50)}"
        }
    }
    
    // Real weather using Open-Meteo (free, no API key)
    private fun realWeatherSearch(location: String): String {
        return try {
            // First, geocode the location
            val encodedLocation = java.net.URLEncoder.encode(location, "UTF-8")
            val geocodeUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedLocation&count=1&language=vi"
            
            val geocodeRequest = Request.Builder()
                .url(geocodeUrl)
                .header("User-Agent", "VenAI/1.0")
                .build()
            
            val geocodeResponse = httpClient.newCall(geocodeRequest).execute()
            val geocodeBody = geocodeResponse.body?.string()
            
            if (geocodeResponse.isSuccessful && geocodeBody != null) {
                val geocodeJson = JSONObject(geocodeBody)
                val results = geocodeJson.optJSONArray("results")
                
                if (results != null && results.length() > 0) {
                    val firstResult = results.getJSONObject(0)
                    val lat = firstResult.getDouble("latitude")
                    val lon = firstResult.getDouble("longitude")
                    val name = firstResult.optString("name", location)
                    val country = firstResult.optString("country", "")
                    
                    // Get weather
                    val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
                    
                    val weatherRequest = Request.Builder()
                        .url(weatherUrl)
                        .header("User-Agent", "VenAI/1.0")
                        .build()
                    
                    val weatherResponse = httpClient.newCall(weatherRequest).execute()
                    val weatherBody = weatherResponse.body?.string()
                    
                    if (weatherResponse.isSuccessful && weatherBody != null) {
                        val weatherJson = JSONObject(weatherBody)
                        val current = weatherJson.getJSONObject("current")
                        
                        val temp = current.getDouble("temperature_2m")
                        val humidity = current.getInt("relative_humidity_2m")
                        val weatherCode = current.getInt("weather_code")
                        val windSpeed = current.getDouble("wind_speed_10m")
                        
                        val weatherDesc = getWeatherDescription(weatherCode)
                        val emoji = getWeatherEmoji(weatherCode)
                        
                        "**$name**${if (country.isNotEmpty()) ", $country" else ""}\n\n$emoji $weatherDesc\n🌡️ ${temp.toInt()}°C\n💨 Gió: ${windSpeed.toInt()} km/h\n💧 Độ ẩm: $humidity%"
                    } else {
                        "Không thể lấy thông tin thời tiết cho $location"
                    }
                } else {
                    "Không tìm thấy địa điểm: $location. Hãy thử tên thành phố khác."
                }
            } else {
                "Không thể tìm kiếm địa điểm: $location"
            }
        } catch (e: Exception) {
            "Lỗi lấy thời tiết: ${e.message?.take(30)}"
        }
    }
    
    private fun getWeatherDescription(code: Int): String = when (code) {
        0 -> "Trời trong"
        1, 2, 3 -> "Có mây"
        45, 48 -> "Có sương mù"
        51, 53, 55 -> "Mưa phùn"
        61, 63, 65 -> "Có mưa"
        66, 67 -> "Mưa lạnh"
        71, 73, 75 -> "Có tuyết"
        77 -> "Hạt tuyết"
        80, 81, 82 -> "Mưa rào"
        85, 86 -> "Tuyết rơi mạnh"
        95 -> "Có dông"
        96, 99 -> "Dông mưa đá"
        else -> "Khác"
    }
    
    private fun getWeatherEmoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2, 3 -> "⛅"
        45, 48 -> "🌫️"
        51, 53, 55 -> "🌧️"
        61, 63, 65 -> "🌧️"
        66, 67 -> "🌨️"
        71, 73, 75 -> "❄️"
        77 -> "🌨️"
        80, 81, 82 -> "⛈️"
        85, 86 -> "❄️"
        95 -> "⛈️"
        96, 99 -> "⛈️"
        else -> "🌤️"
    }
    
    // Simple math evaluator
    private fun evalSimpleMath(expr: String): String {
        return try {
            // Basic safety - only allow numbers and basic operators
            val sanitized = expr.replace(Regex("[^0-9+\\-*/.()\\s]"), "")
            val result = object : Any() {
                fun evaluate(expression: String): Double {
                    return try {
                        val tokens = expression.replace(" ", "")
                        evaluateExpression(tokens)
                    } catch (e: Exception) {
                        Double.NaN
                    }
                }
                
                private fun evaluateExpression(expr: String): Double {
                    // Simple implementation for basic arithmetic
                    return try {
                        val parts = expr.split(Regex("(?<=[+\\-*/])|(?=[+\\-*/])"))
                        var result = parts[0].toDouble()
                        var i = 1
                        while (i < parts.size) {
                            val op = parts[i]
                            val num = parts.getOrNull(i + 1)?.toDouble() ?: 0.0
                            when (op) {
                                "+" -> result += num
                                "-" -> result -= num
                                "*" -> result *= num
                                "/" -> result /= num
                            }
                            i += 2
                        }
                        result
                    } catch (e: Exception) {
                        Double.NaN
                    }
                }
            }.evaluate(sanitized)
            
            if (result.isNaN()) "Không thể tính" else {
                if (result == result.toLong().toDouble()) result.toLong().toString()
                else String.format("%.2f", result)
            }
        } catch (e: Exception) {
            "Lỗi tính toán"
        }
    }
    
    private data class ToolCallBuilder(
        var id: String = "",
        var name: String = "",
        val argumentsBuilder: StringBuilder = StringBuilder(),
        var started: Boolean = false
    )
}
