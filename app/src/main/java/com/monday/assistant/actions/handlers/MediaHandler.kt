package com.monday.assistant.actions.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.JsonObject
import com.monday.assistant.actions.ActionResult

/**
 * MEDIA HANDLER
 * ─────────────────────────────────────────────────────────────────────
 * Plays music/video on YouTube, Spotify, YouTube Music.
 * Uses deep links for direct search/play.
 */
class MediaHandler(private val context: Context) {

    companion object {
        private const val TAG = "MediaHandler"
    }

    fun play(action: JsonObject): ActionResult {
        val app = action.get("app")?.asString?.lowercase() ?: "youtube"
        val query = action.get("query")?.asString ?: ""

        return when (app) {
            "youtube", "yt" -> playYouTube(query)
            "spotify" -> playSpotify(query)
            "ytmusic", "youtube_music" -> playYouTubeMusic(query)
            else -> playYouTube(query)
        }
    }

    private fun playYouTube(query: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Open YouTube web search as fallback
                val webIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(webIntent)
            }
            ActionResult.success("YouTube-এ '$query' search হচ্ছে")
        } catch (e: Exception) {
            ActionResult.error("YouTube open করতে পারিনি: ${e.message}")
        }
    }

    private fun playSpotify(query: String): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_VIEW,
                Uri.parse("spotify:search:$query")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                val webIntent = Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/${Uri.encode(query)}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(webIntent)
            }
            ActionResult.success("Spotify-তে '$query' search হচ্ছে")
        } catch (e: Exception) {
            ActionResult.error("Spotify open করতে পারিনি")
        }
    }

    private fun playYouTubeMusic(query: String): ActionResult {
        return try {
            val intent = context.packageManager
                .getLaunchIntentForPackage("com.google.android.apps.youtube.music")
                ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                ?: return ActionResult.error("YouTube Music installed nei")
            context.startActivity(intent)
            ActionResult.success("YouTube Music open হয়েছে — '$query' search করো")
        } catch (e: Exception) {
            ActionResult.error("YouTube Music open করতে পারিনি")
        }
    }
}
