package com.meir.logger

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogsAdapter(private var items: List<SessionEntry>) :
    RecyclerView.Adapter<LogsAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtDetail: TextView = view.findViewById(R.id.txtDetail)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        val startDate = Date(entry.startTime)
        holder.txtDate.text = dateFormat.format(startDate)

        val minutes = entry.durationSeconds / 60
        val seconds = entry.durationSeconds % 60
        val durationText = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"

        holder.txtDetail.text = "${timeFormat.format(startDate)} · $durationText · ${entry.label}"
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<SessionEntry>) {
        items = newItems
        notifyDataSetChanged()
    }
}
