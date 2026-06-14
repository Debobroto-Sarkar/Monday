package com.monday.assistant.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ═══════════════════════════════════════════════════════════════════════
 * GEMINI CLIENT — Monday's AI Brain
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Connects to Google Gemini 1.5 Flash API (free tier).
 * Free tier: 1,500 requests/day, 1M tokens/day — more than enough.
 *
 * API Key: Get free from https://aistudio.google.com/app/apikey
 *
 * HOW TO DEBUG:
 * - Enable logging: set LOG_REQUESTS = true
 * - Check logcat tag: "GeminiClient"
 * - Raw request/response will be printed
 *
 * HOW TO SWITCH MODELS:
 * - Change MODEL_NAME to "gemini-1.5-pro" for better reasoning
 * - Change to "gemini-2.0-flash" when available for faster responses
 */
class GeminiClient(private val apiKey: String) {

    companion object {
        private const val TAG = "GeminiClient"
        private const val MODEL_NAME = "gemini-1.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val LOG_REQUESTS = false // Set true to debug API calls

        // Temperature: 0.3 = focused/deterministic (good for commands)
        // Increase to 0.7 for more creative responses
        private const val TEMPERATURE = 0.3f
        private const val MAX_OUTPUT_TOKENS = 1024
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Send a command to Gemini and get Monday's response.
     * Returns a [GeminiResponse] or null if the call failed.
     */
    suspend fun sendCommand(
        userMessage: String,
        systemPrompt: String,
        conversationHistory: List<ConversationTurn> = emptyList()
    ): GeminiResponse? = withContext(Dispatchers.IO) {

        val url = "$BASE_URL/$MODEL_NAME:generateContent?key=$apiKey"

        // Build message array with history + current message
        val contents = buildContents(systemPrompt, conversationHistory, userMessage)

        val requestBody = JsonObject().apply {
            add("contents", gson.toJsonTree(contents))
            add("generationConfig", gson.toJsonTree(
                GenerationConfig(
                    temperature = TEMPERATURE,
                    maxOutputTokens = MAX_OUTPUT_TOKENS,
                    responseMimeType = "application/json"
                )
            ))
        }

        val body = requestBody.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).post(body).build()

        if (LOG_REQUESTS) Log.d(TAG, "Request: ${requestBody}")

        return@withContext try {
            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: return@withContext null

            if (LOG_REQUESTS) Log.d(TAG, "Response: $responseString")

            if (!response.isSuccessful) {
                Log.e(TAG, "API Error ${response.code}: $responseString")
                return@withContext null
            }

            parseGeminiResponse(responseString)
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}", e)
            null
        }
    }

    /**
     * Build the conversation content array for the API request.
     * Includes system prompt as first user message, then history.
     */
    private fun buildContents(
        systemPrompt: String,
        history: List<ConversationTurn>,
        currentMessage: String
    ): List<Map<String, Any>> {
        val contents = mutableListOf<Map<String, Any>>()

        // System prompt injected as first user turn (Gemini API style)
        contents.add(mapOf(
            "role" to "user",
            "parts" to listOf(mapOf("text" to systemPrompt))
        ))
        contents.add(mapOf(
            "role" to "model",
            "parts" to listOf(mapOf("text" to "{\"speech\": \"Monday ready. How can I help?\", \"action\": null}"))
        ))

        // Add conversation history (last 10 turns for context)
        history.takeLast(10).forEach { turn ->
            contents.add(mapOf(
                "role" to turn.role,
                "parts" to listOf(mapOf("text" to turn.content))
            ))
        }

        // Add current user message
        contents.add(mapOf(
            "role" to "user",
            "parts" to listOf(mapOf("text" to currentMessage))
        ))

        return contents
    }

    /**
     * Parse the raw Gemini API response into a structured [GeminiResponse].
     */
    private fun parseGeminiResponse(raw: String): GeminiResponse? {
        return try {
            val apiResponse = gson.fromJson(raw, GeminiApiResponse::class.java)
            val content = apiResponse.candidates?.firstOrNull()
                ?.content?.parts?.firstOrNull()?.text ?: return null

            // Parse the JSON response from Gemini
            val json = gson.fromJson(content, JsonObject::class.java)

            GeminiResponse(
                speech = json.get("speech")?.asString ?: "",
                actionJson = json.get("action"),
                memoryUpdate = json.get("memory_update")?.asJsonObject,
                isComplete = json.get("complete")?.asBoolean,
                rawJson = content
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: ${e.message}. Raw: $raw")
            null
        }
    }
}

// ─── Data models ─────────────────────────────────────────────────────────────

data class GeminiResponse(
    val speech: String,
    val actionJson: com.google.gson.JsonElement?,
    val memoryUpdate: JsonObject?,
    val isComplete: Boolean?,
    val rawJson: String
)

data class ConversationTurn(
    val role: String, // "user" or "model"
    val content: String
)

private data class GenerationConfig(
    val temperature: Float,
    val maxOutputTokens: Int,
    @SerializedName("response_mime_type")
    val responseMimeType: String
)

private data class GeminiApiResponse(
    val candidates: List<Candidate>?
)

private data class Candidate(
    val content: Content?
)

private data class Content(
    val parts: List<Part>?,
    val role: String?
)

private data class Part(
    val text: String?
)
