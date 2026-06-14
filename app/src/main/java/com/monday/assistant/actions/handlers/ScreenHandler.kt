package com.monday.assistant.actions.handlers

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.monday.assistant.actions.ActionResult
import com.monday.assistant.services.MondayAccessibilityService

/**
 * SCREEN HANDLER
 * ─────────────────────────────────────────────────────────────────────
 * Uses MondayAccessibilityService to interact with the screen.
 * Taps elements, types text, scrolls, reads content.
 *
 * This is the "agentic" part — Monday can see and control anything on screen.
 */
class ScreenHandler(private val context: Context) {

    companion object {
        private const val TAG = "ScreenHandler"
    }

    private val accessibility get() = MondayAccessibilityService.instance

    fun tap(action: JsonObject): ActionResult {
        val text = action.get("text")?.asString ?: return ActionResult.error("No tap target")
        val service = accessibility ?: return ActionResult.error("Accessibility service not running")

        return if (service.tapByText(text)) {
            ActionResult.success("'$text' tap হয়েছে")
        } else {
            ActionResult.error("'$text' element পাইনি screen-এ")
        }
    }

    fun type(action: JsonObject): ActionResult {
        val text = action.get("text")?.asString ?: return ActionResult.error("No text to type")
        val service = accessibility ?: return ActionResult.error("Accessibility service not running")

        return if (service.typeText(text)) {
            ActionResult.success("Text type হয়েছে")
        } else {
            ActionResult.error("Text field পাইনি")
        }
    }

    fun scroll(action: JsonObject): ActionResult {
        val direction = action.get("direction")?.asString ?: "down"
        val service = accessibility ?: return ActionResult.error("Accessibility service not running")

        return if (service.scroll(direction)) {
            ActionResult.success()
        } else {
            ActionResult.error("Scroll করতে পারিনি")
        }
    }

    fun read(): ActionResult {
        val service = accessibility ?: return ActionResult.error("Accessibility service not running")
        val content = service.readScreenContent()

        return if (content.isNotBlank()) {
            ActionResult.success(content)
        } else {
            ActionResult.error("Screen content পড়তে পারিনি")
        }
    }
}
