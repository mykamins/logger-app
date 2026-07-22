package com.meir.logger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ArchiveWeekAdapter(
    private val items: List<ArchiveWeek>,
    private val onClick: (ArchiveWeek) -> Unit
) : RecyclerView.Adapter<ArchiveWeekAdapter.ViewHolder>() {

    private val displayFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtWeekTitle)
        val txtCount: TextView = view.findViewById(R.id.txtWeekCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_archive_week, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val week = items[position]
        val monday = LocalDate.parse(week.weekLabel)
        val sunday = monday.plusDays(6)
        holder.txtTitle.text = "Week of ${monday.format(displayFormat)} \u2013 ${sunday.format(displayFormat)}"
        holder.txtCount.text = "${week.count} session${if (week.count == 1) "" else "s"}"
        holder.itemView.setOnClickListener { onClick(week) }
    }

    override fun getItemCount(): Int = items.size
}
