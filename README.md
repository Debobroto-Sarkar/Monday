# Monday — Your Personal AI Assistant 🤖

A full-featured voice AI assistant for Android, built for personal use on Samsung S23 Ultra.
Understands Bengali, English, and Banglish. Controls your entire phone via voice.

---

## Features

- 🎙️ Voice commands in Bengali, English, and Banglish
- 🧠 Powered by Google Gemini (free tier)
- 🔊 JARVIS-style male voice (Microsoft Azure Neural TTS)
- 👁️ Full phone control via Accessibility Service
- 📬 Read and reply to notifications without opening apps
- 🧠 Personal memory — learns your habits over time
- 📱 Set as default assistant (replaces Bixby)
- 🔔 Auto-starts on phone boot

---

## Setup Guide

### Step 1: Install the APK

1. Go to the **Releases** section of this GitHub repo
2. Download `app-debug.apk`
3. On your S23 Ultra: **Settings → Apps → Menu (⋮) → Special access → Install unknown apps → Chrome → Allow**
4. Open the downloaded APK and tap **Install**

### Step 2: Get Your Free Gemini API Key

1. Go to [aistudio.google.com](https://aistudio.google.com/app/apikey)
2. Sign in with your Google account
3. Click **Create API Key**
4. Copy the key (starts with `AIza...`)

### Step 3: Get Your Free Azure TTS Key (Optional but recommended for JARVIS voice)

1. Go to [azure.microsoft.com/free](https://azure.microsoft.com/free/)
2. Create a free account (no credit card needed for free tier)
3. Search for **Cognitive Services → Speech Service**
4. Create resource → Copy Key 1
5. Note your **Region** (e.g., `eastus`)

### Step 4: Configure Monday

1. Open the Monday app
2. Enter your **Gemini API Key**
3. Enter your **Azure TTS Key** (optional — skip for basic voice)
4. Tap **Get Started**

### Step 5: Enable Required Permissions

Monday needs 3 special permissions. Each is a one-time toggle:

**1. Accessibility Service (most important)**
> Settings → Accessibility → Installed services → Monday → Turn ON

**2. Notification Access**
> Settings → Apps → Menu (⋮) → Special access → Notification access → Monday → Allow

**3. Set as Default Assistant**
> Settings → General management → Default apps → Digital assistant app → Monday

---

## How to Use

Just say **"Hey Monday"** or long-press the **Home button**:

| Command | What happens |
|---|---|
| `YouTube e MrBeast er video play koro` | Opens YouTube, searches MrBeast |
| `Tanvir ke WhatsApp e message diye dao: kal asbo na` | Sends WhatsApp message |
| `Ke message dise?` | Reads latest notification |
| `Reply diye dao: thik ache` | Replies without opening app |
| `Blip e amar last 3ta photo pathao` | Shares last 3 photos via Blip |
| `Flashlight on koro` | Turns on torch |
| `Volume 50% e set koro` | Sets volume to 50% |
| `Alarm diye dao kal 7 tar jonno` | Sets 7 AM alarm |
| `Dhaka er weather ki?` | Gemini answers |

---

## Project Structure (for developers)

```
app/src/main/java/com/monday/assistant/
├── MondayApp.kt                    # Application class
├── ai/
│   ├── GeminiClient.kt             # Gemini API brain
│   ├── AzureTtsClient.kt           # JARVIS voice
│   ├── MemoryManager.kt            # Personal memory (Room DB)
│   └── SystemPrompts.kt            # Monday's personality
├── services/
│   ├── AssistantBackgroundService.kt  # Main orchestrator
│   ├── MondayAccessibilityService.kt  # Screen reader/controller
│   ├── MondayNotificationService.kt   # Notification listener
│   └── MondayVoiceInteractionService.kt  # Default assistant
├── actions/
│   ├── ActionExecutor.kt           # Command dispatcher
│   └── handlers/                   # One file per capability
│       ├── AppLaunchHandler.kt
│       ├── MessagingHandler.kt
│       ├── NotificationHandler.kt
│       ├── SystemControlHandler.kt
│       ├── FileHandler.kt
│       ├── MediaHandler.kt
│       ├── WebHandler.kt
│       ├── AlarmHandler.kt
│       └── ScreenHandler.kt
└── core/
    ├── ContactResolver.kt          # Bengali name matching
    ├── VoiceRecognitionManager.kt  # Voice input
    └── BootReceiver.kt             # Auto-start on boot
```

## Adding a New Feature

1. Create `actions/handlers/MyNewHandler.kt`
2. Register it in `ActionExecutor.kt` (one line in the `when` block)
3. Add the action type to `ai/SystemPrompts.kt`
4. Push to GitHub → APK builds automatically

## Debugging

- Enable `LOG_REQUESTS = true` in `GeminiClient.kt` to see all API calls
- Enable `LOG_SCREEN_EVENTS = true` in `MondayAccessibilityService.kt` to see UI events
- Call `memoryManager.dumpMemory()` to see stored memory
- Call `accessibilityService.dumpScreenContent()` to see current screen state

---

## Privacy

All memory is stored **locally on your phone** in a Room database.  
Voice is processed by Google's Speech Recognition servers.  
Commands are sent to Google Gemini API servers.  
Nothing is stored by third parties permanently.
