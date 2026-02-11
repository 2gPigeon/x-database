package com.example.x_database.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoPlayer(filePath: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            VideoView(context).apply {
                val controller = MediaController(context)
                controller.setAnchorView(this)
                setMediaController(controller)
                setVideoPath(filePath)
                setOnPreparedListener { player ->
                    player.isLooping = true
                    start()
                }
            }
        },
        update = { view ->
            if (view.tag != filePath) {
                view.tag = filePath
                view.setVideoPath(filePath)
                view.setOnPreparedListener { player ->
                    player.isLooping = true
                    view.start()
                }
            }
        }
    )
}
