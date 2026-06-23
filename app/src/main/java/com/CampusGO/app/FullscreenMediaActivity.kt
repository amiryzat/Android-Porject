package com.CampusGO.app

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.VideoView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class FullscreenMediaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fullscreen_media)

        val ivFullscreen = findViewById<ImageView>(R.id.ivFullscreen)
        val videoViewFullscreen = findViewById<VideoView>(R.id.videoViewFullscreen)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        btnBack.setOnClickListener {
            finish()
        }

        val mediaType = intent.getStringExtra("MEDIA_TYPE") ?: ""
        val mediaContent = intent.getStringExtra("MEDIA_CONTENT") ?: ""

        if (mediaType == "IMAGE") {
            ivFullscreen.visibility = View.VISIBLE
            videoViewFullscreen.visibility = View.GONE
            try {
                val decodedBytes = Base64.decode(mediaContent, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    ivFullscreen.setImageBitmap(bitmap)
                } else {
                    ivFullscreen.setImageResource(android.R.drawable.ic_menu_gallery)
                }
            } catch (e: Exception) {
                ivFullscreen.setImageResource(android.R.drawable.ic_menu_gallery)
            }
        } else if (mediaType == "VIDEO") {
            ivFullscreen.visibility = View.GONE
            videoViewFullscreen.visibility = View.VISIBLE
            progressBar.visibility = View.VISIBLE

            try {
                val mediaController = MediaController(this)
                mediaController.setAnchorView(videoViewFullscreen)
                videoViewFullscreen.setMediaController(mediaController)
                videoViewFullscreen.setVideoURI(Uri.parse(mediaContent))

                videoViewFullscreen.setOnPreparedListener {
                    progressBar.visibility = View.GONE
                    videoViewFullscreen.start()
                }

                videoViewFullscreen.setOnErrorListener { _, _, _ ->
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Failed to load video", Toast.LENGTH_SHORT).show()
                    true
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error playing video", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
