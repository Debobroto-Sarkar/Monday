package com.monday.assistant.actions.handlers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.util.Log
import com.google.gson.JsonObject
import com.monday.assistant.actions.ActionResult
import java.util.Calendar

/**
 * ALARM HANDLER
 * ─────────────────────────────────────────────────────────────────────
 * Sets alarms, timers, and reminders using Android's AlarmClock intent.
 * Works with the phone's default clock app.
 */
class AlarmHandler(private val context: Context) {

    companion object {
        private const val TAG = "AlarmHandler"
    }

    fun setAlarm(action: JsonObject): ActionResult {
        val hour = action.get("hour")?.asInt ?: return ActionResult.error("Hour missing")
        val minute = action.get("minute")?.asInt ?: 0
        val label = action.get("label")?.asString ?: "Monday Alarm"

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minute)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true) // Set without UI confirmation
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val timeStr = String.format("%02d:%02d", hour, minute)
            ActionResult.success("Alarm set হয়েছে $timeStr-এ ✓")
        } catch (e: Exception) {
            Log.e(TAG, "Alarm error: ${e.message}")
            ActionResult.error("Alarm set করতে পারিনি")
        }
    }

    fun setTimer(action: JsonObject): ActionResult {
        val seconds = action.get("seconds")?.asInt ?: return ActionResult.error("Duration missing")
        val label = action.get("label")?.asString ?: "Monday Timer"

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            val displayTime = when {
                seconds >= 3600 -> "${seconds / 3600} hour${if (seconds / 3600 > 1) "s" else ""}"
                seconds >= 60 -> "${seconds / 60} minute${if (seconds / 60 > 1) "s" else ""}"
                else -> "$seconds seconds"
            }
            ActionResult.success("Timer set হয়েছে $displayTime-এর জন্য ✓")
        } catch (e: Exception) {
            Log.e(TAG, "Timer error: ${e.message}")
            ActionResult.error("Timer set করতে পারিনি")
        }
    }
}
