package com.example.x_database.ui

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@Composable
fun VideoThumbnail(filePath: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                val thumb = ImageView(context).apply {
                    scaleType = ScaleType.CENTER_CROP
                }
                val play = ImageView(context).apply {
                    scaleType = ScaleType.CENTER
                    setImageResource(android.R.drawable.ic_media_play)
                }
                addView(thumb, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                addView(play, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                })
                tag = thumb
            }
        },
        update = { view ->
            val thumbView = view.tag as ImageView
            val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(File(filePath), Size(360, 360), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND)
            }
            thumbView.setImageBitmap(bitmap)
        }
    )
}
