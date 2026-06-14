package com.monday.assistant.ai

import android.content.Context
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * ═══════════════════════════════════════════════════════════════════════
 * MEMORY MANAGER — Monday Learns About You Over Time
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Stores personal preferences, habits, and learned context locally on
 * the phone. This is injected into every Gemini request so Monday gets
 * smarter the more you use it.
 *
 * WHAT GETS REMEMBERED:
 * - Contact app preferences (Tanvir = WhatsApp, not call)
 * - Custom shortcuts ("morning routine" = X, Y, Z)
 * - User profile (name, city, schedule)
 * - Correction history (what Monday got wrong and how to fix it)
 * - Frequently used commands (for faster recognition)
 *
 * DATA IS STORED LOCALLY — never leaves your phone except as part of
 * Gemini API requests (which go to Google's servers).
 *
 * HOW TO DEBUG MEMORY:
 * - Call dumpMemory() to see everything stored
 * - Call clearAll() to wipe and start fresh
 */
class MemoryManager(context: Context) {

    companion object {
        private const val TAG = "MemoryManager"

        // Memory categories — add new ones here for new feature areas
        const val CAT_CONTACT_PREF = "contact_pref"     // app preferences per contact
        const val CAT_SHORTCUT = "shortcut"              // user-defined shortcuts
        const val CAT_PROFILE = "profile"                // user's personal info
        const val CAT_CORRECTION = "correction"          // past mistakes + fixes
        const val CAT_HABIT = "habit"                    // detected usage patterns
        const val CAT_APP_PREF = "app_pref"             // preferred apps for actions
        const val CAT_SCHEDULE = "schedule"              // user's schedule info
    }

    private val db = MemoryDatabase.getInstance(context)
    private val dao = db.memoryDao()

    // ─── Write operations ─────────────────────────────────────────────────────

    /**
     * Save a memory entry. Overwrites existing entry with same key.
     *
     * Example:
     * save(CAT_CONTACT_PREF, "tanvir_app", "whatsapp")
     * save(CAT_PROFILE, "user_city", "Dhaka")
     * save(CAT_SHORTCUT, "morning_routine", "weather,notifications,youtube")
     */
    suspend fun save(category: String, key: String, value: String) {
        withContext(Dispatchers.IO) {
            dao.insert(MemoryEntry(
                id = "$category:$key",
                category = category,
                key = key,
                value = value,
                updatedAt = System.currentTimeMillis()
            ))
            Log.d(TAG, "Saved: [$category] $key = $value")
        }
    }

    /**
     * Save multiple memory entries from a Gemini memory_update response.
     * Called automatically when Gemini returns a memory_update block.
     */
    suspend fun saveFromGemini(updates: Map<String, String>) {
        updates.forEach { (key, value) ->
            // Key format from Gemini: "category:actual_key" or just "key"
            val parts = key.split(":", limit = 2)
            if (parts.size == 2) {
                save(parts[0], parts[1], value)
            } else {
                save(CAT_HABIT, key, value)
            }
        }
    }

    // ─── Read operations ──────────────────────────────────────────────────────

    /** Get a specific memory value. */
    suspend fun get(category: String, key: String): String? =
        withContext(Dispatchers.IO) {
            dao.get("$category:$key")?.value
        }

    /** Get all memory entries in a category. */
    suspend fun getCategory(category: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            dao.getByCategory(category).associate { it.key to it.value }
        }

    /**
     * Build the full memory context string to inject into Gemini prompts.
     * This is the key function — called before every Gemini request.
     */
    suspend fun buildMemoryContext(): String = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        if (all.isEmpty()) return@withContext "No personal memory stored yet."

        val sb = StringBuilder()

        // Group by category for readability
        val grouped = all.groupBy { it.category }

        grouped[CAT_PROFILE]?.let { entries ->
            sb.appendLine("USER PROFILE:")
            entries.forEach { sb.appendLine("  ${it.key}: ${it.value}") }
        }

        grouped[CAT_CONTACT_PREF]?.let { entries ->
            sb.appendLine("CONTACT PREFERENCES:")
            entries.forEach { sb.appendLine("  ${it.key}: ${it.value}") }
        }

        grouped[CAT_APP_PREF]?.let { entries ->
            sb.appendLine("APP PREFERENCES:")
            entries.forEach { sb.appendLine("  ${it.key}: ${it.value}") }
        }

        grouped[CAT_SHORTCUT]?.let { entries ->
            sb.appendLine("CUSTOM SHORTCUTS:")
            entries.forEach { sb.appendLine("  '${it.key}' means: ${it.value}") }
        }

        grouped[CAT_HABIT]?.let { entries ->
            sb.appendLine("LEARNED HABITS:")
            entries.forEach { sb.appendLine("  ${it.key}: ${it.value}") }
        }

        grouped[CAT_CORRECTION]?.let { entries ->
            sb.appendLine("PAST CORRECTIONS (avoid repeating these mistakes):")
            entries.forEach { sb.appendLine("  ${it.key}: ${it.value}") }
        }

        grouped[CAT_SCHEDULE]?.let { entries ->
            sb.appendLine("USER SCHEDULE:")
            entries.forEach { sb.appendLine("  ${it.key}: ${it.value}") }
        }

        sb.toString().trimEnd()
    }

    // ─── Utility operations ───────────────────────────────────────────────────

    /** Log all stored memory to logcat (for debugging). */
    suspend fun dumpMemory() = withContext(Dispatchers.IO) {
        val all = dao.getAll()
        Log.d(TAG, "═══ MEMORY DUMP (${all.size} entries) ═══")
        all.forEach { Log.d(TAG, "[${it.category}] ${it.key} = ${it.value}") }
    }

    /** Clear all stored memory. */
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.deleteAll()
        Log.d(TAG, "Memory cleared.")
    }

    /** Clear a specific category. */
    suspend fun clearCategory(category: String) = withContext(Dispatchers.IO) {
        dao.deleteByCategory(category)
    }

    /** Get total number of memory entries. */
    suspend fun count(): Int = withContext(Dispatchers.IO) { dao.count() }
}

// ─── Room Database Entities & DAOs ───────────────────────────────────────────

@Entity(tableName = "memory")
data class MemoryEntry(
    @PrimaryKey val id: String,
    val category: String,
    val key: String,
    val value: String,
    val updatedAt: Long
)

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MemoryEntry)

    @Query("SELECT * FROM memory WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MemoryEntry?

    @Query("SELECT * FROM memory WHERE category = :category ORDER BY updatedAt DESC")
    suspend fun getByCategory(category: String): List<MemoryEntry>

    @Query("SELECT * FROM memory ORDER BY category, key")
    suspend fun getAll(): List<MemoryEntry>

    @Query("DELETE FROM memory WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM memory WHERE category = :category")
    suspend fun deleteByCategory(category: String)

    @Query("DELETE FROM memory")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM memory")
    suspend fun count(): Int
}

@Database(entities = [MemoryEntry::class], version = 1, exportSchema = false)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var INSTANCE: MemoryDatabase? = null

        fun getInstance(context: Context): MemoryDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    MemoryDatabase::class.java,
                    "monday_memory.db"
                ).build().also { INSTANCE = it }
            }
    }
}
