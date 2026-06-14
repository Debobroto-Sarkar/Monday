package com.monday.assistant.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.monday.assistant.databinding.ActivitySettingsBinding
import com.monday.assistant.services.AssistantBackgroundService

/**
 * ═══════════════════════════════════════════════════════════════════════
 * SETTINGS ACTIVITY — API Key setup & Permission shortcuts
 * ═══════════════════════════════════════════════════════════════════════
 *
 * First screen users see if Monday is not configured.
 * Saves keys to SharedPreferences and starts the service.
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val SETTINGS_PREFS = "monday_settings"
    }

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSavedKeys()
        setupClickListeners()
    }

    private fun loadSavedKeys() {
        val prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        val geminiKey = prefs.getString("gemini_api_key", "") ?: ""
        val azureKey = prefs.getString("azure_tts_key", "") ?: ""
        val azureRegion = prefs.getString("azure_region", "eastus") ?: "eastus"

        // Show masked version if key already saved
        if (geminiKey.isNotBlank()) {
            binding.etGeminiKey.hint = "Key saved ✓ (enter to change)"
        }
        if (azureKey.isNotBlank()) {
            binding.etAzureKey.hint = "Key saved ✓ (enter to change)"
        }
        binding.etAzureRegion.setText(azureRegion)
    }

    private fun setupClickListeners() {
        // Save button
        binding.btnSave.setOnClickListener {
            saveAndStart()
        }

        // Permission shortcuts
        binding.btnAccessibility.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Settings → Accessibility → Monday", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnNotificationAccess.setOnClickListener {
            try {
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            } catch (e: Exception) {
                Toast.makeText(this, "Settings → Apps → Notification access → Monday", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnDefaultAssistant.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Settings → General management → Default apps → Digital assistant", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun saveAndStart() {
        val geminiKey = binding.etGeminiKey.text.toString().trim()
        val azureKey = binding.etAzureKey.text.toString().trim()
        val azureRegion = binding.etAzureRegion.text.toString().trim()
            .ifBlank { "eastus" }

        // Validate Gemini key
        if (geminiKey.isBlank()) {
            binding.etGeminiKey.error = "Gemini API key required"
            return
        }

        // Save to SharedPreferences
        val prefs = getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("gemini_api_key", geminiKey)
            if (azureKey.isNotBlank()) putString("azure_tts_key", azureKey)
            putString("azure_region", azureRegion)
            apply()
        }

        // Show feedback
        binding.tvStatus.text = "✓ Saved! Starting Monday…"

        // Restart service with new keys
        AssistantBackgroundService.stop(this)
        binding.root.postDelayed({
            AssistantBackgroundService.start(this)
            Toast.makeText(this, "Monday is ready! 🚀", Toast.LENGTH_SHORT).show()
            finish()
        }, 500)
    }
}
