package com.monday.assistant.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

/**
 * ═══════════════════════════════════════════════════════════════════════
 * MONDAY ACCESSIBILITY SERVICE — Eyes & Hands of Monday
 * ═══════════════════════════════════════════════════════════════════════
 *
 * This is what makes Monday truly powerful. With this service enabled,
 * Monday can:
 * - See every UI element on screen (buttons, text, inputs)
 * - Tap any element by text or description
 * - Type text into any input field
 * - Scroll in any direction
 * - Swipe gestures
 * - Navigate back/home
 * - Read all screen content
 *
 * USER SETUP: Settings → Accessibility → Installed Services → Monday → ON
 *
 * HOW TO ADD NEW SCREEN INTERACTIONS:
 * 1. Use findNode(text) to locate an element
 * 2. Use clickNode(node) to tap it
 * 3. Use typeText(text) to type in focused field
 * 4. Use readScreen() to get all text content
 *
 * HOW TO DEBUG:
 * - Enable LOG_SCREEN_EVENTS = true to see every UI event
 * - Use dumpScreenContent() to print full UI tree to logcat
 */
class MondayAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityService"
        private const val LOG_SCREEN_EVENTS = false // Set true to debug UI events

        // Singleton reference — other classes use this to perform actions
        @Volatile
        var instance: MondayAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Stores last captured screen content for Gemini's agentic loop
    private var lastScreenContent: String = ""
    private var lastPackageName: String = ""

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Monday Accessibility Service connected ✓")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        scope.cancel()
        Log.d(TAG, "Monday Accessibility Service destroyed")
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (LOG_SCREEN_EVENTS) {
            Log.d(TAG, "Event: ${event.eventType} | pkg: ${event.packageName}")
        }

        // Update cached screen content when screen changes
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lastPackageName = event.packageName?.toString() ?: ""
                scope.launch(Dispatchers.Default) {
                    lastScreenContent = readScreenContent()
                }
            }
        }
    }

    // ─── Screen Reading ───────────────────────────────────────────────────────

    /**
     * Read all visible text content on the current screen.
     * Used by Gemini's agentic loop to understand current state.
     */
    fun readScreenContent(): String {
        val root = rootInActiveWindow ?: return ""
        val sb = StringBuilder()
        collectNodeText(root, sb, 0)
        return sb.toString().trim()
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, sb: StringBuilder, depth: Int) {
        node ?: return
        val indent = "  ".repeat(depth.coerceAtMost(5))

        // Collect text content
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val hint = node.hintText?.toString()

        when {
            !text.isNullOrBlank() -> sb.appendLine("$indent[TEXT] $text")
            !desc.isNullOrBlank() -> sb.appendLine("$indent[DESC] $desc")
            !hint.isNullOrBlank() -> sb.appendLine("$indent[HINT] $hint")
        }

        // Recurse into children
        for (i in 0 until node.childCount) {
            collectNodeText(node.getChild(i), sb, depth + 1)
        }
    }

    /** Get cached screen content (non-blocking). */
    fun getLastScreenContent(): String = lastScreenContent

    /** Get the package of the currently active app. */
    fun getCurrentPackage(): String = lastPackageName

    // ─── Finding Elements ─────────────────────────────────────────────────────

    /**
     * Find a UI node by its visible text.
     * Case-insensitive partial match.
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return findNodeByTextRecursive(root, text.lowercase())
    }

    private fun findNodeByTextRecursive(
        node: AccessibilityNodeInfo?,
        searchText: String
    ): AccessibilityNodeInfo? {
        node ?: return null

        val nodeText = node.text?.toString()?.lowercase() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (nodeText.contains(searchText) || nodeDesc.contains(searchText)) {
            if (node.isClickable) return node
        }

        for (i in 0 until node.childCount) {
            val found = findNodeByTextRecursive(node.getChild(i), searchText)
            if (found != null) return found
        }
        return null
    }

    /**
     * Find all clickable nodes on screen.
     * Useful for debugging what Monday can interact with.
     */
    fun findAllClickableNodes(): List<String> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<String>()
        collectClickable(root, results)
        return results
    }

    private fun collectClickable(node: AccessibilityNodeInfo?, results: MutableList<String>) {
        node ?: return
        if (node.isClickable) {
            val label = node.text?.toString()
                ?: node.contentDescription?.toString()
                ?: "unlabeled"
            results.add(label)
        }
        for (i in 0 until node.childCount) collectClickable(node.getChild(i), results)
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    /**
     * Tap a UI element by its text label.
     * Returns true if element was found and clicked.
     */
    fun tapByText(text: String): Boolean {
        val node = findNodeByText(text) ?: run {
            Log.w(TAG, "Could not find element with text: '$text'")
            return false
        }
        return clickNode(node)
    }

    /**
     * Click an AccessibilityNodeInfo.
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d(TAG, "Clicked: ${node.text ?: node.contentDescription}")
            true
        } else {
            // Try clicking the parent if node itself isn't clickable
            val parent = node.parent
            if (parent?.isClickable == true) {
                parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                true
            } else {
                // Last resort: use gesture tap at center of node bounds
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                tapAtCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
        }
    }

    /**
     * Type text into the currently focused input field.
     */
    fun typeText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false

        // Find focused/editable node
        val focused = findFocusedEditText(root) ?: run {
            Log.w(TAG, "No focused text field found for typing")
            return false
        }

        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findFocusedEditText(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isEditable && (node.isFocused || node.isAccessibilityFocused)) return node
        if (node.isEditable) return node // Take first editable as fallback
        for (i in 0 until node.childCount) {
            val found = findFocusedEditText(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * Scroll in a direction on the current screen.
     */
    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root)

        val action = when (direction.lowercase()) {
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        return scrollable?.performAction(action) ?: false
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    /**
     * Tap at exact screen coordinates using a gesture.
     */
    fun tapAtCoordinates(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Swipe gesture.
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Press the Back button.
     */
    fun pressBack() = performGlobalAction(GLOBAL_ACTION_BACK)

    /**
     * Press the Home button.
     */
    fun pressHome() = performGlobalAction(GLOBAL_ACTION_HOME)

    /**
     * Open Recents/Overview.
     */
    fun openRecents() = performGlobalAction(GLOBAL_ACTION_RECENTS)

    /**
     * Pull down notification shade.
     */
    fun openNotificationShade() = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    /**
     * Dump full UI tree to logcat (for debugging new app interactions).
     */
    fun dumpScreenContent() {
        Log.d(TAG, "═══ SCREEN DUMP ═══")
        Log.d(TAG, "Package: $lastPackageName")
        Log.d(TAG, readScreenContent())
        Log.d(TAG, "Clickable elements: ${findAllClickableNodes()}")
    }
}
