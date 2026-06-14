package com.monday.assistant.services

import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log

/**
 * ═══════════════════════════════════════════════════════════════════════
 * MONDAY VOICE INTERACTION SERVICE — Default Assistant Integration
 * ═══════════════════════════════════════════════════════════════════════
 *
 * This makes Monday available as the "Default Digital Assistant" in
 * Android Settings → General management → Default apps → Digital assistant.
 *
 * When set as default:
 * → Long-pressing Home button opens Monday (instead of Bixby/Google)
 * → "Hey Monday" hotword can trigger it
 * → Works from lock screen
 */
class MondayVoiceInteractionService : VoiceInteractionService() {

    companion object {
        private const val TAG = "VoiceInteractionService"
    }

    override fun onReady() {
        super.onReady()
        Log.d(TAG, "Monday voice interaction service ready")
    }

    override fun onShutdown() {
        super.onShutdown()
        Log.d(TAG, "Monday voice interaction service shutdown")
    }
}
