package com.monday.assistant.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * ═══════════════════════════════════════════════════════════════════════
 * AZURE TTS CLIENT — Monday's Voice (JARVIS-style male, Neural quality)
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Uses Microsoft Azure Cognitive Services Neural TTS.
 * Free tier: 500,000 characters/month (≈ 8 hours of speech)
 *
 * Get free key: https://azure.microsoft.com/free/ → Cognitive Services
 *
 * Voices used:
 * - Bengali: bn-BD-PradeepNeural (deep, calm male)
 * - English:  en-US-GuyNeural    (professional male, JARVIS-like)
 *
 * HOW TO ADD A NEW VOICE:
 * - Go to https://speech.microsoft.com/portal
 * - Browse voices → copy voice name
 * - Add to VOICE_MAP below
 *
 * FALLBACK: If Azure key not set, uses Android built-in TTS (still good).
 */
class AzureTtsClient(
    private val context: Context,
    private val subscriptionKey: String,
    private val region: String = "eastus"
) {
    companion object {
        private const val TAG = "AzureTtsClient"

        // Voice map: language code → Azure Neural voice name
        private val VOICE_MAP = mapOf(
            "bn" to "bn-BD-PradeepNeural",   // Bengali male (JARVIS for BD)
            "en" to "en-US-GuyNeural",         // English male (professional)
            "default" to "en-US-GuyNeural"
        )

        // Speaking rate: 0% = normal, -10% = slightly slower (clearer)
        private const val SPEAKING_RATE = "-5%"

        // Pitch: -5% makes voice slightly deeper (more authoritative)
        private const val PITCH = "-5%"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Synthesize text to audio bytes.
     * Returns raw PCM/MP3 audio data, or null if synthesis fails.
     *
     * @param text Text to synthesize
     * @param language Language code: "bn" for Bengali, "en" for English
     */
    suspend fun synthesize(text: String, language: String = "en"): ByteArray? =
        withContext(Dispatchers.IO) {

            if (subscriptionKey.isBlank()) {
                Log.w(TAG, "Azure key not set, falling back to device TTS")
                return@withContext null
            }

            val voice = VOICE_MAP[language] ?: VOICE_MAP["default"]!!
            val ssml = buildSsml(text, voice)
            val url = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"

            val request = Request.Builder()
                .url(url)
                .addHeader("Ocp-Apim-Subscription-Key", subscriptionKey)
                .addHeader("Content-Type", "application/ssml+xml")
                .addHeader("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                .addHeader("User-Agent", "MondayAssistant")
                .post(ssml.toRequestBody("application/ssml+xml".toMediaType()))
                .build()

            return@withContext try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else {
                    Log.e(TAG, "TTS Error ${response.code}: ${response.body?.string()}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS network error: ${e.message}", e)
                null
            }
        }

    /**
     * Build SSML markup for natural-sounding speech.
     * SSML lets us control rate, pitch, pauses, emphasis.
     */
    private fun buildSsml(text: String, voice: String): String {
        // Detect language for locale setting
        val locale = if (voice.startsWith("bn")) "bn-BD" else "en-US"

        // Escape XML special chars
        val escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        return """
            <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" 
                   xmlns:mstts="https://www.w3.org/2001/mstts" xml:lang="$locale">
                <voice name="$voice">
                    <prosody rate="$SPEAKING_RATE" pitch="$PITCH">
                        $escaped
                    </prosody>
                </voice>
            </speak>
        """.trimIndent()
    }

    /**
     * Detect the primary language of a text string.
     * Simple heuristic: if > 20% Bengali Unicode chars → Bengali.
     */
    fun detectLanguage(text: String): String {
        val bengaliChars = text.count { it.code in 0x0980..0x09FF }
        return if (bengaliChars.toFloat() / text.length > 0.2f) "bn" else "en"
    }
}
