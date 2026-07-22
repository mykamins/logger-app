package com.meir.logger

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var dbHelper: DbHelper
    private lateinit var adapter: LogsAdapter
    private lateinit var btnToggle: Button
    private lateinit var txtEmpty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        dbHelper = DbHelper(this)

        val btnEnableAccess = findViewById<Button>(R.id.btnEnableAccess)
        btnToggle = findViewById(R.id.btnToggleMonitoring)
        txtEmpty = findViewById(R.id.txtEmpty)

        val recycler = findViewById<RecyclerView>(R.id.recyclerLogs)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = LogsAdapter(emptyList())
        recycler.adapter = adapter

        btnEnableAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnToggle.setOnClickListener {
            val currentlyEnabled = prefs.getBoolean(Constants.KEY_MONITORING_ENABLED, false)
            prefs.edit().putBoolean(Constants.KEY_MONITORING_ENABLED, !currentlyEnabled).apply()
            updateToggleButtonText()
        }

        findViewById<Button>(R.id.btnViewArchives).setOnClickListener {
            startActivity(Intent(this, ArchivesActivity::class.java))
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.clear_log_confirm_title)
                .setMessage(R.string.clear_log_confirm_message)
                .setPositiveButton(R.string.clear) { _, _ ->
                    dbHelper.clearAll()
                    refreshLogs()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        updateToggleButtonText()
    }

    override fun onResume() {
        super.onResume()
        dbHelper.archiveEntriesBeforeCurrentWeek()
        refreshLogs()
        updateToggleButtonText()
    }

    private fun updateToggleButtonText() {
        val enabled = prefs.getBoolean(Constants.KEY_MONITORING_ENABLED, false)
        btnToggle.text = if (enabled) getString(R.string.stop_monitoring) else getString(R.string.start_monitoring)
    }

    private fun refreshLogs() {
        val sessions = dbHelper.getAllSessions()
        adapter.updateData(sessions)
        txtEmpty.visibility = if (sessions.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }
}
