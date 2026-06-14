package com.monday.assistant.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.monday.assistant.R
import com.monday.assistant.actions.ActionExecutor
import com.monday.assistant.ai.*
import com.monday.assistant.core.ContactResolver
import com.monday.assistant.core.VoiceRecognitionManager
import com.monday.assistant.ui.main.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

/**
 * ═══════════════════════════════════════════════════════════════════════
 * ASSISTANT BACKGROUND SERVICE — Monday's Main Brain
 * ═══════════════════════════════════════════════════════════════════════
 *
 * This is the orchestrator. It ties everything together:
 * 1. Listens for voice input (VoiceRecognitionManager)
 * 2. Reads screen state (MondayAccessibilityService)
 * 3. Sends to Gemini with full context (GeminiClient)
 * 4. Executes the returned action (ActionExecutor)
 * 5. Speaks the response (Azure TTS or Android TTS)
 * 6. Updates memory if Gemini learned something (MemoryManager)
 *
 * Runs as a foreground service so Android won't kill it.
 *
 * HOW TO DEBUG:
 * - logcat tag "AssistantService"
 * - Each step is logged with its result
 */
class AssistantBackgroundService : LifecycleService() {

    companion object {
        private const val TAG = "AssistantService"
        private const val NOTIF_CHANNEL_ID = "monday_service"
        private const val NOTIF_ID = 1001
        private const val SETTINGS_PREFS = "monday_settings"

        fun start(context: Context) {
            val intent = Intent(context, AssistantBackgroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AssistantBackgroundService::class.java))
        }

        // Singleton reference for MainActivity to communicate with
        @Volatile var instance: AssistantBackgroundService? = null
            private set
    }

    // ─── Core components ──────────────────────────────────────────────────────
    private lateinit var geminiClient: GeminiClient
    private lateinit var azureTtsClient: AzureTtsClient
    private lateinit var memoryManager: MemoryManager
    private lateinit var actionExecutor: ActionExecutor
    private lateinit var contactResolver: ContactResolver
    private lateinit var voiceManager: VoiceRecognitionManager
    private var androidTts: TextToSpeech? = null

    // Conversation history for Gemini context (last N turns)
    private val conversationHistory = mutableListOf<ConversationTurn>()

    // Callbacks for UI updates
    var onStateChanged: ((AssistantState) -> Unit)? = null
    var onUserMessage: ((String) -> Unit)? = null
    var onMondayMessage: ((String) -> Unit)? = null

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Monday is ready"))

        initializeComponents()
        Log.d(TAG, "Monday service started ✓")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        voiceManager.destroy()
        androidTts?.shutdown()
        Log.d(TAG, "Monday service stopped")
    }

    override fun onBind(intent: Intent): IBinder? = null

    // ─── Initialization ───────────────────────────────────────────────────────

    private fun initializeComponents() {
        val prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val geminiKey = prefs.getString("gemini_api_key", "") ?: ""
        val azureKey = prefs.getString("azure_tts_key", "") ?: ""
        val azureRegion = prefs.getString("azure_region", "eastus") ?: "eastus"

        geminiClient = GeminiClient(geminiKey)
        azureTtsClient = AzureTtsClient(this, azureKey, azureRegion)
        memoryManager = MemoryManager(this)
        actionExecutor = ActionExecutor(this)
        contactResolver = ContactResolver(this)

        // Load contacts in background
        lifecycleScope.launch(Dispatchers.IO) {
            contactResolver.loadContacts()
        }

        // Initialize Android TTS as fallback
        androidTts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTts?.language = Locale("bn", "BD")
                Log.d(TAG, "Android TTS initialized")
            }
        }

        // Initialize voice recognition
        voiceManager = VoiceRecognitionManager(
            context = this,
            onResult = { text -> onVoiceInput(text) },
            onError = { error -> Log.w(TAG, "Voice error: $error") },
            onListeningStarted = { onStateChanged?.invoke(AssistantState.LISTENING) }
        )
        voiceManager.initialize()
    }

    // ─── Public API (called by MainActivity) ──────────────────────────────────

    /**
     * Start voice listening session.
     */
    fun startListening() {
        onStateChanged?.invoke(AssistantState.LISTENING)
        voiceManager.startListening()
    }

    /**
     * Process a text command (typed or from wake word).
     */
    fun processTextCommand(text: String) {
        onVoiceInput(text)
    }

    // ─── Core Loop ────────────────────────────────────────────────────────────

    private fun onVoiceInput(userText: String) {
        Log.d(TAG, "User: '$userText'")
        onUserMessage?.invoke(userText)
        onStateChanged?.invoke(AssistantState.THINKING)

        lifecycleScope.launch {
            processCommand(userText)
        }
    }

    private suspend fun processCommand(userText: String) {
        try {
            // Build full context for Gemini
            val memoryContext = memoryManager.buildMemoryContext()
            val notifContext = MondayNotificationService.instance
                ?.getPendingNotificationsContext() ?: "Notification access not enabled"
            val screenContent = MondayAccessibilityService.instance
                ?.getLastScreenContent() ?: "Screen content not available"
            val contactContext = contactResolver.buildContactsContext()
            val appContext = "Apps available on phone" // Full list from AppLaunchHandler

            val systemPrompt = SystemPrompts.buildSystemPrompt(
                userMemory = memoryContext,
                installedApps = appContext,
                contacts = contactContext,
                pendingNotifications = notifContext,
                currentScreenContent = screenContent
            )

            // Send to Gemini
            val response = geminiClient.sendCommand(
                userMessage = userText,
                systemPrompt = systemPrompt,
                conversationHistory = conversationHistory
            )

            if (response == null) {
                speakAndNotify("Sorry, Gemini-র সাথে connect করতে পারিনি। Internet check koro.")
                onStateChanged?.invoke(AssistantState.ERROR)
                return
            }

            // Update conversation history
            conversationHistory.add(ConversationTurn("user", userText))
            conversationHistory.add(ConversationTurn("model", response.rawJson))

            // Save memory updates if Gemini learned something
            response.memoryUpdate?.let { updates ->
                val map = updates.entrySet().associate { it.key to it.value.asString }
                memoryManager.saveFromGemini(map)
                Log.d(TAG, "Memory updated: $map")
            }

            // Execute the action
            onStateChanged?.invoke(AssistantState.ACTING)
            val actionResult = actionExecutor.execute(response)

            // Speak the response
            val speechText = if (response.speech.isNotBlank()) response.speech
            else if (actionResult.success) actionResult.message
            else actionResult.message

            speakAndNotify(speechText)
            Log.d(TAG, "Monday: '$speechText'")

        } catch (e: Exception) {
            Log.e(TAG, "Command processing error: ${e.message}", e)
            speakAndNotify("কিছু একটা সমস্যা হয়েছে। আবার try করো।")
            onStateChanged?.invoke(AssistantState.ERROR)
        }
    }

    // ─── Speech Output ────────────────────────────────────────────────────────

    private suspend fun speakAndNotify(text: String) {
        onMondayMessage?.invoke(text)
        onStateChanged?.invoke(AssistantState.SPEAKING)
        updateNotification("Monday: $text")

        // Try Azure TTS first (better quality)
        val language = azureTtsClient.detectLanguage(text)
        val audioBytes = azureTtsClient.synthesize(text, language)

        if (audioBytes != null) {
            playAudioBytes(audioBytes)
        } else {
            // Fallback to Android TTS
            speakWithAndroidTts(text)
        }

        onStateChanged?.invoke(AssistantState.READY)
    }

    private fun playAudioBytes(bytes: ByteArray) {
        try {
            val tempFile = createTempFile("monday_tts", ".mp3", cacheDir)
            tempFile.writeBytes(bytes)

            val player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                setAudioStreamType(AudioManager.STREAM_MUSIC)
                prepare()
                start()
                setOnCompletionListener {
                    release()
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback error: ${e.message}")
            speakWithAndroidTts(bytes.toString())
        }
    }

    private fun speakWithAndroidTts(text: String) {
        androidTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "monday_utterance")
    }

    // ─── Notifications (Service notification, not user notifications) ─────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIF_CHANNEL_ID,
            "Monday Assistant",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Monday is running in background"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Monday")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_monday_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(text.take(100)))
    }
}

enum class AssistantState {
    READY, LISTENING, THINKING, ACTING, SPEAKING, ERROR
}
