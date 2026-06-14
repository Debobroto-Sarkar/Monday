package com.monday.assistant.ui.main

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.monday.assistant.MondayApp
import com.monday.assistant.databinding.ActivityMainBinding
import com.monday.assistant.services.AssistantBackgroundService
import com.monday.assistant.services.AssistantState
import com.monday.assistant.ui.settings.SettingsActivity

/**
 * ═══════════════════════════════════════════════════════════════════════
 * MAIN ACTIVITY — Monday's Beautiful Dark UI
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Features:
 * - Arc reactor pulse dot animation in header
 * - Waveform bars that animate when listening
 * - Chat bubble RecyclerView (user = right/blue, Monday = left/cyan)
 * - Status chip changes color based on state
 * - Mic + text input
 * - Auto-redirects to Settings if not configured
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatAdapter: ChatAdapter

    // Waveform animators
    private val waveformAnimators = mutableListOf<ValueAnimator>()
    // Pulse dot animator
    private var pulseAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // First launch → go to settings
        if (!(application as MondayApp).isConfigured()) {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        setupRecyclerView()
        setupClickListeners()
        startPulseAnimation()
        requestPermissions()
    }

    override fun onResume() {
        super.onResume()
        connectToService()
    }

    override fun onPause() {
        super.onPause()
        stopWaveformAnimation()
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvConversation.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true
            }
        }

        // Welcome message
        chatAdapter.addMessage(
            ChatMessage("Monday is ready. Tap the mic or type a command.", isMonday = true)
        )
    }

    private fun setupClickListeners() {
        // Settings
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Mic button
        binding.btnMic.setOnClickListener {
            val service = AssistantBackgroundService.instance
            if (service != null) {
                service.startListening()
            } else {
                AssistantBackgroundService.start(this)
                binding.root.postDelayed({
                    AssistantBackgroundService.instance?.startListening()
                }, 800)
            }
        }

        // Send button
        binding.btnSend.setOnClickListener { sendTextCommand() }

        // Enter key
        binding.etCommand.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendTextCommand(); true
            } else false
        }
    }

    private fun sendTextCommand() {
        val text = binding.etCommand.text.toString().trim()
        if (text.isBlank()) return
        binding.etCommand.setText("")
        val service = AssistantBackgroundService.instance
        if (service != null) {
            service.processTextCommand(text)
        } else {
            Toast.makeText(this, "Monday is starting…", Toast.LENGTH_SHORT).show()
            AssistantBackgroundService.start(this)
        }
    }

    // ─── Service Connection ────────────────────────────────────────────────────

    private fun connectToService() {
        if (AssistantBackgroundService.instance == null) {
            AssistantBackgroundService.start(this)
        }

        binding.root.postDelayed({
            AssistantBackgroundService.instance?.let { svc ->
                svc.onStateChanged = { state -> runOnUiThread { applyState(state) } }
                svc.onUserMessage = { text ->
                    runOnUiThread {
                        chatAdapter.addMessage(ChatMessage(text, isMonday = false))
                        scrollToBottom()
                    }
                }
                svc.onMondayMessage = { text ->
                    runOnUiThread {
                        chatAdapter.addMessage(ChatMessage(text, isMonday = true))
                        scrollToBottom()
                    }
                }
            }
        }, 600)
    }

    private fun scrollToBottom() {
        binding.rvConversation.postDelayed({
            binding.rvConversation.smoothScrollToPosition(
                (chatAdapter.itemCount - 1).coerceAtLeast(0)
            )
        }, 100)
    }

    // ─── State Visuals ────────────────────────────────────────────────────────

    private fun applyState(state: AssistantState) {
        val (label, chipColor, chipText) = when (state) {
            AssistantState.READY -> Triple("Tap the mic to speak", 0xFF00FF88.toInt(), "READY")
            AssistantState.LISTENING -> Triple("Listening…", 0xFF00C8FF.toInt(), "LISTENING")
            AssistantState.THINKING -> Triple("Thinking…", 0xFFFFB300.toInt(), "THINKING")
            AssistantState.ACTING -> Triple("Doing it…", 0xFFFFB300.toInt(), "ACTING")
            AssistantState.SPEAKING -> Triple("Speaking…", 0xFF00FF88.toInt(), "SPEAKING")
            AssistantState.ERROR -> Triple("Something went wrong", 0xFFFF4444.toInt(), "ERROR")
        }

        binding.tvStateLabel.text = label
        binding.tvStatus.text = chipText
        binding.tvStatus.setBackgroundColor(chipColor)

        // Show/hide waveform bars
        val isListening = state == AssistantState.LISTENING
        binding.waveformBars.visibility =
            if (isListening) android.view.View.VISIBLE else android.view.View.GONE

        if (isListening) startWaveformAnimation() else stopWaveformAnimation()

        // Mic button pulse when listening
        binding.btnMic.alpha = if (isListening) 0.7f else 1f
        binding.btnMic.scaleX = if (isListening) 1.1f else 1f
        binding.btnMic.scaleY = if (isListening) 1.1f else 1f
    }

    // ─── Animations ───────────────────────────────────────────────────────────

    private fun startPulseAnimation() {
        pulseAnimator = ObjectAnimator.ofFloat(binding.pulseDot, "alpha", 1f, 0.2f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun startWaveformAnimation() {
        stopWaveformAnimation()
        val bars = listOf(
            binding.bar1, binding.bar2, binding.bar3, binding.bar4, binding.bar5,
            binding.bar6, binding.bar7, binding.bar8, binding.bar9
        )

        bars.forEachIndexed { index, bar ->
            val minH = dpToPx(8)
            val maxH = dpToPx(44)
            val animator = ValueAnimator.ofInt(minH, maxH).apply {
                duration = 400L + (index * 60L)
                startDelay = index * 80L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    bar.layoutParams = bar.layoutParams.also {
                        it.height = anim.animatedValue as Int
                    }
                    bar.requestLayout()
                }
                start()
            }
            waveformAnimators.add(animator)
        }
    }

    private fun stopWaveformAnimation() {
        waveformAnimators.forEach { it.cancel() }
        waveformAnimators.clear()
    }

    private fun dpToPx(dp: Int) =
        (dp * resources.displayMetrics.density).toInt()

    // ─── Permissions ──────────────────────────────────────────────────────────

    private fun requestPermissions() {
        val perms = listOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 100)
        }
    }
}
