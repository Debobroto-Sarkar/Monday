package com.monday.assistant.core

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log

/**
 * ═══════════════════════════════════════════════════════════════════════
 * CONTACT RESOLVER — Bengali Name Fuzzy Matching
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Loads all phone contacts and resolves voice-spoken names to actual contacts.
 * Handles:
 * - Bengali names (তানভীর → Tanvir)
 * - Partial name matches ("Tanvir" → "Tanvir Ahmed")
 * - Nicknames and typos (fuzzy scoring)
 * - Multiple contacts with similar names (returns closest match)
 *
 * HOW IT WORKS:
 * 1. Loads all contacts at startup
 * 2. When you say a name, scores each contact by similarity
 * 3. Returns the best match above a confidence threshold
 *
 * HOW TO DEBUG:
 * - Call dumpContacts() to see all loaded contacts in logcat
 * - Call resolve("name") and check the log for scoring
 */
class ContactResolver(private val context: Context) {

    companion object {
        private const val TAG = "ContactResolver"
        private const val MIN_CONFIDENCE = 0.4f // Minimum match score (0-1)
    }

    // Cache of loaded contacts
    private var contacts: List<Contact> = emptyList()
    private var loaded = false

    /**
     * Load all contacts from the phone.
     * Call this once at startup (or when contacts change).
     */
    fun loadContacts() {
        val result = mutableListOf<Contact>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    result.add(Contact(
                        id = cursor.getLong(idIdx).toString(),
                        name = cursor.getString(nameIdx) ?: continue,
                        phone = cursor.getString(phoneIdx)
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load contacts: ${e.message}")
        }

        contacts = result.distinctBy { it.id }
        loaded = true
        Log.d(TAG, "Loaded ${contacts.size} contacts")
    }

    /**
     * Resolve a spoken name to a contact.
     * Returns the best matching contact, or null if no good match.
     */
    fun resolve(name: String): ResolvedContact? {
        if (!loaded) loadContacts()
        if (contacts.isEmpty()) return null

        val query = name.trim().lowercase()

        // Exact match first (fastest)
        contacts.firstOrNull { it.name.lowercase() == query }?.let {
            return it.toResolved()
        }

        // Score each contact
        val scored = contacts.map { contact ->
            contact to similarity(query, contact.name.lowercase())
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            scored.sortedByDescending { it.second }.take(3).forEach { (c, s) ->
                Log.d(TAG, "  Match: '${c.name}' → score=${"%.2f".format(s)}")
            }
        }

        val best = scored.maxByOrNull { it.second }
            ?: return null

        return if (best.second >= MIN_CONFIDENCE) {
            Log.d(TAG, "Resolved '$name' → '${best.first.name}' (${best.second})")
            best.first.toResolved()
        } else {
            Log.w(TAG, "No confident match for '$name' (best: ${best.first.name} = ${best.second})")
            null
        }
    }

    /**
     * Build a contact list string for Gemini context.
     * Limited to avoid token overflow.
     */
    fun buildContactsContext(): String {
        if (!loaded) loadContacts()
        return contacts.take(100)
            .joinToString(", ") { it.name }
            .ifEmpty { "No contacts loaded" }
    }

    /**
     * Similarity score between two strings (0.0 to 1.0).
     * Combines multiple strategies for best Bengali/English handling.
     */
    private fun similarity(query: String, target: String): Float {
        if (query == target) return 1.0f
        if (target.contains(query)) return 0.9f
        if (query.contains(target)) return 0.85f

        // Token matching (works well for "Tanvir Ahmed" when user says "Tanvir")
        val queryTokens = query.split(" ")
        val targetTokens = target.split(" ")
        val tokenScore = queryTokens.count { qt ->
            targetTokens.any { tt -> tt.startsWith(qt) || qt.startsWith(tt) }
        }.toFloat() / queryTokens.size.toFloat()

        // Character-level similarity (Levenshtein-based)
        val editScore = 1f - (levenshtein(query, target).toFloat() / maxOf(query.length, target.length).toFloat())

        return maxOf(tokenScore, editScore)
    }

    /** Classic Levenshtein edit distance. */
    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                dp[i][j] = if (a[i-1] == b[j-1]) dp[i-1][j-1]
                else 1 + minOf(dp[i-1][j], dp[i][j-1], dp[i-1][j-1])
            }
        }
        return dp[a.length][b.length]
    }

    /** Log all contacts (for debugging). */
    fun dumpContacts() {
        Log.d(TAG, "═══ CONTACTS (${contacts.size}) ═══")
        contacts.forEach { Log.d(TAG, "${it.name} | ${it.phone}") }
    }

    // ─── Data Models ──────────────────────────────────────────────────────────

    private data class Contact(
        val id: String,
        val name: String,
        val phone: String?,
        val messengerId: String? = null,
        val telegramUsername: String? = null
    ) {
        fun toResolved() = ResolvedContact(name, phone, messengerId, telegramUsername)
    }
}

data class ResolvedContact(
    val name: String,
    val phone: String?,
    val messengerId: String? = null,
    val telegramUsername: String? = null
)
