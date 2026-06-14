package com.monday.assistant.actions.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.monday.assistant.actions.ActionResult

/**
 * APP LAUNCH HANDLER
 * ─────────────────────────────────────────────────────────────────────
 * Opens any installed app by package name or app name.
 *
 * HOW TO DEBUG: Check logcat tag "AppLaunchHandler"
 * HOW TO ADD APP SHORTCUTS: Add to APP_ALIASES map below
 */
class AppLaunchHandler(private val context: Context) {

    companion object {
        private const val TAG = "AppLaunchHandler"

        // Common aliases — maps spoken names to package names
        // Add more as needed for local/regional apps
        private val APP_ALIASES = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "messenger" to "com.facebook.orca",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "telegram" to "org.telegram.messenger",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "tiktok" to "com.zhiliaoapp.musically",
            "snapchat" to "com.snapchat.android",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "camera" to "com.sec.android.app.camera",
            "gallery" to "com.sec.android.gallery3d",
            "settings" to "com.android.settings",
            "calculator" to "com.sec.android.app.popupcalculator",
            "clock" to "com.sec.android.app.clockpackage",
            "calendar" to "com.google.android.calendar",
            "drive" to "com.google.android.apps.docs",
            "blip" to "net.blip.android",  // Blip file transfer
            "bkash" to "com.bKash.customerapp",
            "nagad" to "com.konasl.nagad",
            "discord" to "com.discord",
            "linkedin" to "com.linkedin.android",
            "reddit" to "com.reddit.frontpage",
            "files" to "com.google.android.documentsui",
            "play store" to "com.android.vending",
            "phone" to "com.samsung.android.dialer",
            "contacts" to "com.samsung.android.contacts",
            "messages" to "com.samsung.android.messaging"
        )
    }

    fun launch(action: JsonObject): ActionResult {
        val appName = action.get("appName")?.asString?.lowercase() ?: ""
        val packageName = action.get("package")?.asString ?: ""

        // Try direct package name first
        if (packageName.isNotBlank()) {
            if (launchByPackage(packageName)) {
                return ActionResult.success()
            }
        }

        // Try alias lookup
        val aliasPackage = APP_ALIASES[appName]
        if (aliasPackage != null && launchByPackage(aliasPackage)) {
            return ActionResult.success()
        }

        // Try searching installed apps by name
        val found = findInstalledApp(appName)
        if (found != null && launchByPackage(found)) {
            return ActionResult.success()
        }

        Log.w(TAG, "Could not find app: '$appName' / '$packageName'")
        return ActionResult.error("App '$appName' not found on this phone.")
    }

    private fun launchByPackage(pkg: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "Launched: $pkg")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $pkg: ${e.message}")
            false
        }
    }

    private fun findInstalledApp(name: String): String? {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .firstOrNull { app ->
                    val label = pm.getApplicationLabel(app).toString().lowercase()
                    label.contains(name.lowercase())
                }?.packageName
        } catch (e: Exception) {
            null
        }
    }

    /** Get list of all installed apps (for Gemini context). */
    fun getInstalledAppsContext(): String {
        val pm = context.packageManager
        return try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                .joinToString(", ") { pm.getApplicationLabel(it).toString() }
                .take(2000) // Limit for token budget
        } catch (e: Exception) {
            "Could not load app list"
        }
    }
}
