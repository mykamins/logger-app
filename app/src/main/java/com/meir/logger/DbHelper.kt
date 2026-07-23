package com.meir.logger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class SessionEntry(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val label: String
)

data class ArchiveWeek(
    val weekLabel: String,
    val count: Int
)

class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "logger.db"
        private const val DB_VERSION = 2
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_ARCHIVED = "archived_sessions"

        /**
         * Returns the epoch-millis timestamp for the start (00:00) of the Monday
         * that begins the week containing the given timestamp.
         */
        fun mondayStartMillis(timestampMillis: Long): Long {
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return monday.atStartOfDay(zone).toInstant().toEpochMilli()
        }

        /** ISO date string (yyyy-MM-dd) of the Monday for a given timestamp; used as the grouping key. */
        fun weekLabelFor(timestampMillis: Long): String {
            val zone = ZoneId.systemDefault()
            val date = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate()
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                duration_seconds INTEGER NOT NULL,
                label TEXT
            )
            """.trimIndent()
        )
        createArchiveTable(db)
    }

    private fun createArchiveTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ARCHIVED (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                start_time INTEGER NOT NULL,
                end_time INTEGER NOT NULL,
                duration_seconds INTEGER NOT NULL,
                label TEXT,
                week_label TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Existing "sessions" data is preserved; we only add the new archive table.
        if (oldVersion < 2) {
            createArchiveTable(db)
        }
    }

    fun insertSession(startTime: Long, endTime: Long, label: String) {
        val db = writableDatabase
        val mergeWindowMs = 60_000L

        // If the previous entry ended within the merge window of this one starting,
        // treat it as the same continuous visit: stretch the existing entry's end
        // time instead of adding a new row. This keeps the log accurate even if
        // detection briefly stumbles mid-visit (any live-detection quirk still
        // gets absorbed here rather than showing up as a pile of tiny entries).
        val cursor = db.query(
            TABLE_SESSIONS, null, null, null, null, null,
            "start_time DESC", "1"
        )
        var mergedIntoId: Long? = null
        cursor.use {
            if (it.moveToFirst()) {
                val prevId = it.getLong(it.getColumnIndexOrThrow("id"))
                val prevEnd = it.getLong(it.getColumnIndexOrThrow("end_time"))
                val prevStart = it.getLong(it.getColumnIndexOrThrow("start_time"))
                if (startTime - prevEnd in 0..mergeWindowMs) {
                    val newDuration = (endTime - prevStart) / 1000
                    val values = ContentValues().apply {
                        put("end_time", endTime)
                        put("duration_seconds", newDuration)
                        put("label", label)
                    }
                    db.update(TABLE_SESSIONS, values, "id = ?", arrayOf(prevId.toString()))
                    mergedIntoId = prevId
                }
            }
        }

        if (mergedIntoId == null) {
            val durationSeconds = (endTime - startTime) / 1000
            val values = ContentValues().apply {
                put("start_time", startTime)
                put("end_time", endTime)
                put("duration_seconds", durationSeconds)
                put("label", label)
            }
            db.insert(TABLE_SESSIONS, null, values)
        }
    }

    fun getAllSessions(): List<SessionEntry> {
        return querySessions(TABLE_SESSIONS)
    }

    fun clearAll() {
        writableDatabase.delete(TABLE_SESSIONS, null, null)
    }

    /**
     * Moves any session that started before the current week's Monday into the
     * weekly archive, grouped by the Monday of the week it actually occurred in.
     * Safe to call as often as you like: once an entry is archived it's gone
     * from the main table, so repeat calls have nothing left to move.
     */
    fun archiveEntriesBeforeCurrentWeek() {
        val currentWeekStart = mondayStartMillis(System.currentTimeMillis())
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cursor = db.query(
                TABLE_SESSIONS,
                null,
                "start_time < ?",
                arrayOf(currentWeekStart.toString()),
                null, null, null
            )
            cursor.use {
                while (it.moveToNext()) {
                    val startTime = it.getLong(it.getColumnIndexOrThrow("start_time"))
                    val endTime = it.getLong(it.getColumnIndexOrThrow("end_time"))
                    val durationSeconds = it.getLong(it.getColumnIndexOrThrow("duration_seconds"))
                    val label = it.getString(it.getColumnIndexOrThrow("label")) ?: ""

                    val values = ContentValues().apply {
                        put("start_time", startTime)
                        put("end_time", endTime)
                        put("duration_seconds", durationSeconds)
                        put("label", label)
                        put("week_label", weekLabelFor(startTime))
                    }
                    db.insert(TABLE_ARCHIVED, null, values)
                }
            }
            db.delete(TABLE_SESSIONS, "start_time < ?", arrayOf(currentWeekStart.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getArchiveWeeks(): List<ArchiveWeek> {
        val list = mutableListOf<ArchiveWeek>()
        val cursor = readableDatabase.rawQuery(
            "SELECT week_label, COUNT(*) as cnt FROM $TABLE_ARCHIVED GROUP BY week_label ORDER BY week_label DESC",
            null
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    ArchiveWeek(
                        weekLabel = it.getString(it.getColumnIndexOrThrow("week_label")),
                        count = it.getInt(it.getColumnIndexOrThrow("cnt"))
                    )
                )
            }
        }
        return list
    }

    fun getArchivedSessionsForWeek(weekLabel: String): List<SessionEntry> {
        return querySessions(TABLE_ARCHIVED, "week_label = ?", arrayOf(weekLabel))
    }

    private fun querySessions(
        table: String,
        selection: String? = null,
        selectionArgs: Array<String>? = null
    ): List<SessionEntry> {
        val list = mutableListOf<SessionEntry>()
        val cursor = readableDatabase.query(
            table,
            null, selection, selectionArgs, null, null,
            "start_time DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SessionEntry(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        startTime = it.getLong(it.getColumnIndexOrThrow("start_time")),
                        endTime = it.getLong(it.getColumnIndexOrThrow("end_time")),
                        durationSeconds = it.getLong(it.getColumnIndexOrThrow("duration_seconds")),
                        label = it.getString(it.getColumnIndexOrThrow("label")) ?: ""
                    )
                )
            }
        }
        return list
    }
}
