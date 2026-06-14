package com.monday.assistant.actions

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.monday.assistant.actions.handlers.*
import com.monday.assistant.ai.GeminiResponse
import kotlinx.coroutines.delay

/**
 * ═══════════════════════════════════════════════════════════════════════
 * ACTION EXECUTOR — Monday's Command Dispatcher
 * ═══════════════════════════════════════════════════════════════════════
 *
 * This is the central dispatcher. When Gemini returns an action JSON,
 * ActionExecutor routes it to the correct handler.
 *
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ HOW TO ADD A NEW ACTION TYPE (the plugin system):               │
 * │                                                                 │
 * │ 1. Create a new handler: actions/handlers/MyNewHandler.kt       │
 * │ 2. Add the action name to the when() block below               │
 * │ 3. Add the action type to SystemPrompts.kt ACTION_TYPES        │
 * │ 4. Done! Monday can now perform the new action                 │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * HOW TO DEBUG:
 * - Set LOG_ACTIONS = true to see every action dispatched
 * - Each handler has its own TAG for logcat filtering
 */
class ActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "ActionExecutor"
        private const val LOG_ACTIONS = true // Always log for easy debugging
        private const val MAX_AGENTIC_STEPS = 10 // Safety: max steps per task
    }

    private val gson = Gson()

    // ─── Handler instances (lazy-initialized, one instance per handler) ───────
    private val appLaunchHandler by lazy { AppLaunchHandler(context) }
    private val messagingHandler by lazy { MessagingHandler(context) }
    private val notificationHandler by lazy { NotificationHandler(context) }
    private val systemControlHandler by lazy { SystemControlHandler(context) }
    private val fileHandler by lazy { FileHandler(context) }
    private val mediaHandler by lazy { MediaHandler(context) }
    private val webHandler by lazy { WebHandler(context) }
    private val alarmHandler by lazy { AlarmHandler(context) }
    private val screenHandler by lazy { ScreenHandler(context) }

    /**
     * Execute an action from a Gemini response.
     * Returns a result message to speak back to the user.
     */
    suspend fun execute(response: GeminiResponse): ActionResult {
        val actionJson = response.actionJson ?: return ActionResult.success(response.speech)
        val action = actionJson.asJsonObject ?: return ActionResult.success(response.speech)
        val actionType = action.get("action")?.asString ?: return ActionResult.error("Unknown action")

        if (LOG_ACTIONS) Log.d(TAG, "Executing: $actionType | $actionJson")

        return try {
            when (actionType) {
                // ── App Control ──────────────────────────────────────────────
                "LAUNCH_APP" -> appLaunchHandler.launch(action)

                // ── Messaging ────────────────────────────────────────────────
                "SEND_MESSAGE" -> messagingHandler.sendMessage(action)
                "MAKE_CALL" -> messagingHandler.makeCall(action)

                // ── Notifications ────────────────────────────────────────────
                "REPLY_NOTIF" -> notificationHandler.reply(action)
                "READ_NOTIFS" -> notificationHandler.read(action)
                "DISMISS_NOTIF" -> notificationHandler.dismiss(action)

                // ── Screen Interaction (Accessibility Service) ────────────────
                "TAP_ELEMENT" -> screenHandler.tap(action)
                "TYPE_TEXT" -> screenHandler.type(action)
                "SCROLL" -> screenHandler.scroll(action)
                "READ_SCREEN" -> screenHandler.read()

                // ── System Controls ───────────────────────────────────────────
                "SYSTEM_CONTROL" -> systemControlHandler.control(action)

                // ── Files ─────────────────────────────────────────────────────
                "SHARE_FILES" -> fileHandler.shareFiles(action)
                "OPEN_FILE" -> fileHandler.openFile(action)

                // ── Media ─────────────────────────────────────────────────────
                "PLAY_MEDIA" -> mediaHandler.play(action)

                // ── Web ───────────────────────────────────────────────────────
                "WEB_SEARCH" -> webHandler.search(action)
                "OPEN_URL" -> webHandler.openUrl(action)

                // ── Productivity ──────────────────────────────────────────────
                "SET_ALARM" -> alarmHandler.setAlarm(action)
                "SET_TIMER" -> alarmHandler.setTimer(action)

                // ── AI Direct Answer ──────────────────────────────────────────
                "ANSWER" -> ActionResult.success(action.get("text")?.asString ?: response.speech)

                // ── Multi-step agentic task ───────────────────────────────────
                "MULTI_STEP" -> executeMultiStep(action)

                // ── Memory (handled by caller, but acknowledge here) ───────────
                "SAVE_MEMORY" -> ActionResult.success("Got it, I'll remember that.")

                // ── Unknown action (for debugging / new features) ─────────────
                else -> {
                    Log.w(TAG, "Unknown action type: $actionType")
                    ActionResult.error("I don't know how to do that yet. Coming soon!")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action failed: $actionType | ${e.message}", e)
            ActionResult.error("Something went wrong: ${e.message}")
        }
    }

    /**
     * Execute a sequence of steps for complex multi-step tasks.
     * Example: "Open Blip → select files → send"
     */
    private suspend fun executeMultiStep(action: JsonObject): ActionResult {
        val steps = action.getAsJsonArray("steps") ?: return ActionResult.error("No steps found")

        if (steps.size() > MAX_AGENTIC_STEPS) {
            Log.w(TAG, "Too many steps (${steps.size()}), capping at $MAX_AGENTIC_STEPS")
        }

        val results = mutableListOf<String>()

        for (i in 0 until minOf(steps.size(), MAX_AGENTIC_STEPS)) {
            val step = steps[i].asJsonObject
            val stepAction = step.get("action")?.asString ?: continue

            Log.d(TAG, "Multi-step [$i/${steps.size()}]: $stepAction")

            // Wrap step as a fake GeminiResponse for recursive execution
            val fakeResponse = GeminiResponse(
                speech = "",
                actionJson = step,
                memoryUpdate = null,
                isComplete = null,
                rawJson = step.toString()
            )

            val result = execute(fakeResponse)
            results.add(result.message)

            if (!result.success) {
                Log.w(TAG, "Multi-step failed at step $i: ${result.message}")
                break
            }

            // Small delay between steps to let UI settle
            delay(500)
        }

        return ActionResult.success(results.lastOrNull() ?: "Done!")
    }
}

// ─── Result Model ─────────────────────────────────────────────────────────────

data class ActionResult(
    val success: Boolean,
    val message: String
) {
    companion object {
        fun success(message: String = "Done!") = ActionResult(true, message)
        fun error(message: String) = ActionResult(false, message)
    }
}
