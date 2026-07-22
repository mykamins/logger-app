package com.meir.logger

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ArchivesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DbHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_archives)

        dbHelper = DbHelper(this)

        val recycler = findViewById<RecyclerView>(R.id.recyclerWeeks)
        recycler.layoutManager = LinearLayoutManager(this)

        val weeks = dbHelper.getArchiveWeeks()
        findViewById<TextView>(R.id.txtEmptyArchives).visibility =
            if (weeks.isEmpty()) View.VISIBLE else View.GONE

        recycler.adapter = ArchiveWeekAdapter(weeks) { week ->
            val intent = Intent(this, ArchiveDetailActivity::class.java)
            intent.putExtra("week_label", week.weekLabel)
            startActivity(intent)
        }
    }
}
