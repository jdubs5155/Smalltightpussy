package com.zim.jackettprowler

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TorrentAdapter(
    private var items: List<TorrentResult>,
    private val onClick: (TorrentResult) -> Unit
) : RecyclerView.Adapter<TorrentAdapter.TorrentViewHolder>() {

    inner class TorrentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        val textSize: TextView = itemView.findViewById(R.id.textSize)
        val textHealth: TextView = itemView.findViewById(R.id.textHealth)
        val textSeeders: TextView = itemView.findViewById(R.id.textSeeders)
        val textLeechers: TextView = itemView.findViewById(R.id.textLeechers)
        val textIndexer: TextView = itemView.findViewById(R.id.textIndexer)
        val textCategory: TextView = itemView.findViewById(R.id.textCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_torrent, parent, false)
        return TorrentViewHolder(view)
    }

    override fun onBindViewHolder(holder: TorrentViewHolder, position: Int) {
        val item = items[position]
        
        // Title
        holder.textTitle.text = item.title
        
        // Size
        holder.textSize.text = item.sizePretty()
        
        // Health status
        val healthStatus = item.getHealthStatus()
        val healthColor = item.getHealthColor()
        holder.textHealth.text = healthStatus
        holder.textHealth.setTextColor(healthColor)
        
        // Seeders (with up arrow)
        holder.textSeeders.text = "↑ ${item.seeders}"
        holder.textSeeders.setTextColor(
            if (item.seeders > 0) Color.parseColor("#00AA00") else Color.parseColor("#999999")
        )
        
        // Leechers (with down arrow)
        val leecherCount = if (item.leechers > 0) item.leechers else item.peers
        holder.textLeechers.text = "↓ $leecherCount"
        holder.textLeechers.setTextColor(Color.parseColor("#FF6600"))
        
        // Indexer
        val indexerDisplay = item.indexer ?: "Unknown"
        holder.textIndexer.text = indexerDisplay
        
        // Category (show if available)
        if (item.category.isNotEmpty()) {
            holder.textCategory.visibility = View.VISIBLE
            holder.textCategory.text = "📁 ${item.category}"
        } else {
            holder.textCategory.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<TorrentResult>) {
        items = newItems
        notifyDataSetChanged()
    }
}
