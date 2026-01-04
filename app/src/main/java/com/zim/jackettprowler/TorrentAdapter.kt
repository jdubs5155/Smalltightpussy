package com.zim.jackettprowler

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
        val textSubtitle: TextView = itemView.findViewById(R.id.textSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorrentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_torrent, parent, false)
        return TorrentViewHolder(view)
    }

    override fun onBindViewHolder(holder: TorrentViewHolder, position: Int) {
        val item = items[position]
        val indexerDisplay = item.indexer ?: "unknown"
        holder.textTitle.text = item.title
        holder.textSubtitle.text =
            "${item.sizePretty()} • Seeders: ${item.seeders} • $indexerDisplay"

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<TorrentResult>) {
        items = newItems
        notifyDataSetChanged()
    }
}
