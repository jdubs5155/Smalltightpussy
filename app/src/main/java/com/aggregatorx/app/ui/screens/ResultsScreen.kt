package com.aggregatorx.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.paging.PagingData
import androidx.paging.compose.items
import coil3.compose.rememberAsyncImagePainter
import com.aggregatorx.app.data.model.VideoItem

@Composable
fun VideoFeed(results: PagingData<VideoItem>) {
    val context = LocalContext.current
    LazyColumn {
        items(results) { item ->
            VideoThumbnail(item.thumbnail)
            Text(item.title)
            Button(onClick = { playFullScreen(item.url, context) }) {
                Text("Play")
            }
        }
    }
}

@Composable
fun VideoThumbnail(thumbnailUrl: String) {
    Image(
        painter = rememberAsyncImagePainter(thumbnailUrl),
        contentDescription = "Video Thumbnail"
    )
}

fun playFullScreen(url: String, context: Context) {
    try {
        val player = ExoPlayer.Builder(context).build()
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        // Add try-catch + release on back
    } catch (e: Exception) {
        Toast.makeText(context, "Play failed", Toast.LENGTH_SHORT).show()
    }
}