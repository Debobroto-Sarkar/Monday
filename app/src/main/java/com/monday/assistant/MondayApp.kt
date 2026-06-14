package com.monday.assistant

import android.app.Application
import android.content.Context
import android.util.Log
import com.monday.assistant.ai.MemoryManager
import com.monday.assistant.core.ContactResolver
import com.monday.assistant.services.AssistantBackgroundService

/**
 * ═══════════════════════════════════════════════════════════════════════
 * MONDAY APPLICATION CLASS
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Application-level initialization. Runs when the app first starts.
 * Initializes shared singletons and starts the background service.
 */
class MondayApp : Application() {

    companion object {
        private const val TAG = "MondayApp"
        private const val SETTINGS_PREFS = "monday_settings"

        lateinit var instance: MondayApp
            private set
    }

    lateinit var memoryManager: MemoryManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "Monday starting up…")

        // Initialize memory manager (Room DB)
        memoryManager = MemoryManager(this)

        // Auto-start the assistant service if API key is configured
        if (isConfigured()) {
            AssistantBackgroundService.start(this)
        }

        Log.d(TAG, "Monday ready ✓")
    }

    /** Returns true if the app has been set up with a Gemini API key. */
    fun isConfigured(): Boolean {
        val prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        return prefs.getString("gemini_api_key", "")?.isNotBlank() == true
    }
}
