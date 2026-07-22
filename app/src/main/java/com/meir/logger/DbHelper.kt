package com.meir.logger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SessionEntry(
    val id: Long,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val label: String
)

class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "logger.db"
        private const val DB_VERSION = 1
        const val TABLE_SESSIONS = "sessions"
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }

    fun insertSession(startTime: Long, endTime: Long, label: String) {
        val durationSeconds = (endTime - startTime) / 1000
        val values = ContentValues().apply {
            put("start_time", startTime)
            put("end_time", endTime)
            put("duration_seconds", durationSeconds)
            put("label", label)
        }
        writableDatabase.insert(TABLE_SESSIONS, null, values)
    }

    fun getAllSessions(): List<SessionEntry> {
        val list = mutableListOf<SessionEntry>()
        val cursor = readableDatabase.query(
            TABLE_SESSIONS,
            null, null, null, null, null,
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

    fun clearAll() {
        writableDatabase.delete(TABLE_SESSIONS, null, null)
    }
}
