package com.monday.assistant.actions.handlers

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.provider.Settings
import android.util.Log
import com.google.gson.JsonObject
import com.monday.assistant.actions.ActionResult

/**
 * SYSTEM CONTROL HANDLER
 * ─────────────────────────────────────────────────────────────────────
 * Controls phone system settings:
 * - WiFi on/off
 * - Bluetooth on/off
 * - Flashlight/torch
 * - Volume (media, ringtone, alarm)
 * - Brightness
 * - Do Not Disturb
 * - Mobile Data (opens Settings panel)
 * - Airplane mode (opens Settings)
 *
 * HOW TO ADD NEW CONTROLS:
 * 1. Add a case in control()
 * 2. Implement the control logic
 * 3. Add to SystemPrompts ACTION_TYPES description
 */
class SystemControlHandler(private val context: Context) {

    companion object {
        private const val TAG = "SystemControlHandler"
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var isTorchOn = false
    private var torchCameraId: String? = null

    fun control(action: JsonObject): ActionResult {
        val controlType = action.get("control")?.asString?.lowercase()
            ?: return ActionResult.error("Control type missing")
        val value = action.get("value")?.asString?.lowercase() ?: ""

        return when (controlType) {
            "torch", "flashlight" -> controlTorch(value)
            "volume", "volume_media" -> controlVolume(value, AudioManager.STREAM_MUSIC)
            "volume_ring", "ringtone" -> controlVolume(value, AudioManager.STREAM_RING)
            "volume_alarm" -> controlVolume(value, AudioManager.STREAM_ALARM)
            "wifi" -> controlWifi(value)
            "bluetooth", "bt" -> controlBluetooth(value)
            "dnd", "do_not_disturb" -> controlDnd(value)
            "brightness" -> controlBrightness(value)
            "data", "mobile_data" -> openMobileDataSettings()
            "airplane", "airplane_mode" -> openAirplaneSettings()
            else -> ActionResult.error("Unknown control: $controlType")
        }
    }

    private fun controlTorch(value: String): ActionResult {
        return try {
            if (torchCameraId == null) {
                torchCameraId = cameraManager.cameraIdList.firstOrNull()
            }
            val cameraId = torchCameraId ?: return ActionResult.error("Camera not found")

            val turnOn = when (value) {
                "on" -> true
                "off" -> false
                else -> !isTorchOn // Toggle if no value specified
            }

            cameraManager.setTorchMode(cameraId, turnOn)
            isTorchOn = turnOn
            ActionResult.success("Flashlight ${if (turnOn) "on" else "off"} হয়েছে")
        } catch (e: Exception) {
            Log.e(TAG, "Torch error: ${e.message}")
            ActionResult.error("Flashlight control করতে পারিনি")
        }
    }

    private fun controlVolume(value: String, streamType: Int): ActionResult {
        val maxVol = audioManager.getStreamMaxVolume(streamType)
        val newVol = when {
            value == "max" || value == "100" -> maxVol
            value == "min" || value == "0" || value == "mute" -> 0
            value.endsWith("%") -> (value.dropLast(1).toIntOrNull() ?: 50) * maxVol / 100
            value.toIntOrNull() != null -> value.toInt() * maxVol / 100
            value == "up" -> (audioManager.getStreamVolume(streamType) + maxVol / 10).coerceAtMost(maxVol)
            value == "down" -> (audioManager.getStreamVolume(streamType) - maxVol / 10).coerceAtLeast(0)
            else -> maxVol / 2
        }

        audioManager.setStreamVolume(streamType, newVol, AudioManager.FLAG_SHOW_UI)
        val percent = (newVol * 100 / maxVol)
        return ActionResult.success("Volume $percent%-এ set হয়েছে")
    }

    private fun controlWifi(value: String): ActionResult {
        // Android 10+ requires going to Settings panel for WiFi toggle
        val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.success("WiFi settings open হয়েছে")
        } catch (e: Exception) {
            ActionResult.error("WiFi settings খুলতে পারিনি")
        }
    }

    private fun controlBluetooth(value: String): ActionResult {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            ActionResult.success("Bluetooth settings open হয়েছে")
        } catch (e: Exception) {
            ActionResult.error("Bluetooth settings খুলতে পারিনি")
        }
    }

    private fun controlDnd(value: String): ActionResult {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.success("Do Not Disturb settings open হয়েছে")
    }

    private fun controlBrightness(value: String): ActionResult {
        return try {
            val max = 255
            val brightness = when {
                value == "max" -> max
                value == "min" -> 0
                value.endsWith("%") -> (value.dropLast(1).toIntOrNull() ?: 50) * max / 100
                value.toIntOrNull() != null -> value.toInt() * max / 100
                else -> max / 2
            }

            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness.coerceIn(0, max)
            )
            ActionResult.success("Brightness set হয়েছে")
        } catch (e: Exception) {
            ActionResult.error("Brightness change করতে Write Settings permission লাগবে")
        }
    }

    private fun openMobileDataSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.success("Mobile data settings open হয়েছে")
    }

    private fun openAirplaneSettings(): ActionResult {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return ActionResult.success("Airplane mode settings open হয়েছে")
    }
}
