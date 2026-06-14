package com.monday.assistant.actions.handlers

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.monday.assistant.actions.ActionResult
import com.monday.assistant.services.MondayNotificationService

/**
 * NOTIFICATION HANDLER
 * ─────────────────────────────────────────────────────────────────────
 * Handles all notification-related voice commands:
 * - "ke message dise?" → reads latest notification
 * - "ki likhse?" → reads message content
 * - "reply diye dao: ..." → sends direct reply
 * - "clear koro" → dismisses notifications
 */
class NotificationHandler(private val context: Context) {

    companion object {
        private const val TAG = "NotificationHandler"
    }

    private val service get() = MondayNotificationService.instance

    fun read(action: JsonObject): ActionResult {
        val notifService = service ?: return ActionResult.error(
            "Notification access enabled nei. Settings → Apps → Notification access → Monday ON koro."
        )

        val filter = action.get("filter")?.asString ?: "all"
        val context = notifService.getPendingNotificationsContext()

        return if (context.contains("No pending")) {
            ActionResult.success("Kono noti নেই")
        } else {
            ActionResult.success(context)
        }
    }

    fun reply(action: JsonObject): ActionResult {
        val notifService = service ?: return ActionResult.error("Notification access enabled nei")

        val notifId = action.get("notifId")?.asString
        val replyText = action.get("reply")?.asString
            ?: return ActionResult.error("Reply text missing")

        val notif = if (notifId != null) {
            notifService.findBySender(notifId) ?: notifService.getMostRecent()
        } else {
            notifService.getMostRecent()
        } ?: return ActionResult.error("Kono notification paini")

        if (!notif.supportsReply) {
            return ActionResult.error("${notif.appName} direct reply support kore na. App khulte hobe.")
        }

        val success = notifService.replyToNotification(notif.id, replyText)
        return if (success) {
            ActionResult.success("Reply pathano hoyeche ✓")
        } else {
            ActionResult.error("Reply pathate parini")
        }
    }

    fun dismiss(action: JsonObject): ActionResult {
        val notifService = service ?: return ActionResult.error("Notification access enabled nei")
        val id = action.get("notifId")?.asString ?: "all"

        return if (id == "all") {
            notifService.dismissAll()
            ActionResult.success("Shob notification clear hoyeche")
        } else {
            val notif = notifService.findBySender(id)
            if (notif != null) {
                notifService.dismissNotification(notif.id)
                ActionResult.success("Notification dismiss hoyeche")
            } else {
                ActionResult.error("Notification paini")
            }
        }
    }
}
