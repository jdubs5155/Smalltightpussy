package com.zim.jackettprowler.video

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zim.jackettprowler.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.graphics.BitmapFactory

/**
 * Adapter for displaying video search results
 */
class VideoResultAdapter(
    private val onItemClick: (VideoResult) -> Unit
) : RecyclerView.Adapter<VideoResultAdapter.ViewHolder>() {
    
    private var results = listOf<VideoResult>()
    private var groupedBySource = false
    private val client = OkHttpClient()
    
    fun updateData(newResults: List<VideoResult>, grouped: Boolean = false) {
        results = if (grouped) {
            // Group by source
            newResults.sortedBy { it.source }
        } else {
            newResults
        }
        groupedBySource = grouped
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video_result, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(results[position])
    }
    
    override fun getItemCount() = results.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.imageThumbnail)
        private val textTitle: TextView = itemView.findViewById(R.id.textVideoTitle)
        private val textChannel: TextView = itemView.findViewById(R.id.textChannel)
        private val textDuration: TextView = itemView.findViewById(R.id.textDuration)
        private val textViews: TextView = itemView.findViewById(R.id.textViews)
        private val textSource: TextView = itemView.findViewById(R.id.textSource)
        
        fun bind(result: VideoResult) {
            textTitle.text = result.title
            textChannel.text = result.channel.ifEmpty { "Unknown" }
            textDuration.text = result.getFormattedDuration()
            textViews.text = if (result.views.isNotEmpty()) "${result.getFormattedViews()} views" else ""
            textSource.text = result.source
            
            // Set source badge color based on site type
            textSource.setBackgroundColor(getSourceColor(result.siteType))
            
            // Load thumbnail async
            if (result.thumbnailUrl.isNotEmpty()) {
                loadThumbnail(result.thumbnailUrl, thumbnail)
            } else {
                thumbnail.setImageResource(R.drawable.ic_video_placeholder)
            }
            
            itemView.setOnClickListener {
                onItemClick(result)
            }
            
            itemView.setOnLongClickListener {
                // Open in browser
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.videoUrl))
                itemView.context.startActivity(intent)
                true
            }
        }
        
        private fun loadThumbnail(url: String, imageView: ImageView) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bitmap = BitmapFactory.decodeStream(response.body?.byteStream())
                            withContext(Dispatchers.Main) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        imageView.setImageResource(R.drawable.ic_video_placeholder)
                    }
                }
            }
        }
        
        private fun getSourceColor(type: VideoSiteType): Int {
            return when (type) {
                VideoSiteType.YOUTUBE -> 0xFFFF0000.toInt()      // Red
                VideoSiteType.DAILYMOTION -> 0xFF0066DC.toInt()  // Blue
                VideoSiteType.VIMEO -> 0xFF1AB7EA.toInt()        // Light blue
                VideoSiteType.RUMBLE -> 0xFF85C742.toInt()       // Green
                VideoSiteType.ODYSEE -> 0xFFE50914.toInt()       // Red-orange
                VideoSiteType.BITCHUTE -> 0xFFF27405.toInt()     // Orange
                VideoSiteType.PEERTUBE -> 0xFFF27405.toInt()     // Orange
                VideoSiteType.ARCHIVE_ORG -> 0xFF666666.toInt()  // Gray
                VideoSiteType.TWITCH -> 0xFF9146FF.toInt()       // Purple
                VideoSiteType.GENERIC -> 0xFF888888.toInt()      // Gray
            }
        }
    }
}
