package com.agentstudio.domain.model

data class ToolResult(
    val success: Boolean,
    val output: String = "",
    val error: String = "",
    val metadata: Map<String, String> = emptyMap()
) {
    val isError: Boolean
        get() = !success
    
    override fun toString(): String {
        return if (success) {
            output
        } else {
            "Error: $error"
        }
    }
    
    fun toJsonString(): String {
        return if (success) {
            buildString {
                append("{\"success\":true,\"output\":")
                append("\"${output.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\"")
                if (metadata.isNotEmpty()) {
                    append(",\"metadata\":{")
                    append(metadata.entries.joinToString(",") { (k, v) ->
                        "\"$k\":\"${v.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                    })
                    append("}")
                }
                append("}")
            }
        } else {
            "{\"success\":false,\"error\":\"${error.replace("\\", "\\\\").replace("\"", "\\\"")}\"}"
        }
    }
    
    companion object {
        fun success(output: String, metadata: Map<String, String> = emptyMap()): ToolResult {
            return ToolResult(success = true, output = output, metadata = metadata)
        }
        
        fun error(error: String): ToolResult {
            return ToolResult(success = false, error = error)
        }
    }
}
