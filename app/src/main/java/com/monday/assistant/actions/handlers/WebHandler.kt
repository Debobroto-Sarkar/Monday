package com.monday.assistant.actions.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.monday.assistant.actions.ActionResult

/**
 * WEB HANDLER
 * ─────────────────────────────────────────────────────────────────────
 * Opens URLs and performs web searches in Chrome/browser.
 */
class WebHandler(private val context: Context) {

    companion object {
        private const val TAG = "WebHandler"
    }

    fun search(action: JsonObject): ActionResult {
        val query = action.get("query")?.asString ?: return ActionResult.error("No search query")
        return openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
    }

    fun openUrl(action: JsonObject): ActionResult {
        val url = action.get("url")?.asString ?: return ActionResult.error("No URL")
        return openUrl(url)
    }

    private fun openUrl(url: String): ActionResult {
        return try {
            val finalUrl = if (url.startsWith("http")) url else "https://$url"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.success("Browser open হয়েছে")
        } catch (e: Exception) {
            ActionResult.error("URL open করতে পারিনি: ${e.message}")
        }
    }
}
