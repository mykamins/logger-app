package com.meir.logger

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ArchiveDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archive_detail)

        val weekLabel = intent.getStringExtra("week_label") ?: return
        val dbHelper = DbHelper(this)
        val sessions = dbHelper.getArchivedSessionsForWeek(weekLabel)

        val monday = LocalDate.parse(weekLabel)
        val sunday = monday.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
        findViewById<android.widget.TextView>(R.id.txtWeekHeader).text =
            "Week of ${monday.format(fmt)} \u2013 ${sunday.format(fmt)}"

        val recycler = findViewById<RecyclerView>(R.id.recyclerArchiveDetail)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = LogsAdapter(sessions)
    }
}
