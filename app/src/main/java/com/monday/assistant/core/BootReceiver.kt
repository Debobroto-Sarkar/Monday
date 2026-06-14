package com.monday.assistant.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.monday.assistant.services.AssistantBackgroundService

/**
 * BOOT RECEIVER
 * ─────────────────────────────────────────────────────────────────────
 * Auto-starts Monday when the phone boots.
 * This ensures Monday is always running without the user having to
 * manually open the app each time they restart their phone.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootReceiver", "Phone booted — starting Monday")
            if ((context.applicationContext as? com.monday.assistant.MondayApp)?.isConfigured() == true) {
                AssistantBackgroundService.start(context)
            }
        }
    }
}
