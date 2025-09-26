package com.example.videoinopenglpoc

import android.graphics.RectF
import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    private lateinit var glSurfaceView: GLSurfaceView
    private lateinit var videoRenderer: VideoRenderer

    // 用於動態平移的狀態
    private var currentShiftX = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        glSurfaceView = findViewById(R.id.glSurfaceView)
        glSurfaceView.setEGLContextClientVersion(2)

        videoRenderer = VideoRenderer(this, glSurfaceView)
        glSurfaceView.setRenderer(videoRenderer)
        glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        setupButtons()
    }

    private fun setupButtons() {
        findViewById<Button>(R.id.btnFullscreen).setOnClickListener {
            val viewWidth = glSurfaceView.width.toFloat()
            val viewHeight = glSurfaceView.height.toFloat()
            val rect = RectF(0f, 0f, viewWidth, viewHeight)
            videoRenderer.setVideoRect(rect)
        }

        findViewById<Button>(R.id.btnTopLeft).setOnClickListener {
            val viewWidth = glSurfaceView.width.toFloat()
            val viewHeight = glSurfaceView.height.toFloat()
            // 顯示在左上角四分之一的區域
            val rect = RectF(0f, 0f, viewWidth / 2, viewHeight / 2)
            videoRenderer.setVideoRect(rect)
        }

        findViewById<Button>(R.id.btnShift).setOnClickListener {
            val viewWidth = glSurfaceView.width.toFloat()
            val viewHeight = glSurfaceView.height.toFloat()

            // 每次點擊向右移動 50 像素，超出範圍則歸零
            currentShiftX += 50f
            if (currentShiftX + (viewWidth / 2) > viewWidth) {
                currentShiftX = 0f
            }

            // 在上半部分，大小為四分之一，位置動態變化
            val rect = RectF(currentShiftX, 0f, currentShiftX + viewWidth / 2, viewHeight / 2)
            videoRenderer.setVideoRect(rect)
        }

        findViewById<Button>(R.id.btnZoomPan).setOnClickListener {
            val viewWidth = glSurfaceView.width.toFloat()
            val viewHeight = glSurfaceView.height.toFloat()

            // 1. Define a scale factor. Let's make it 1.5x larger than the screen.
            val scale = 1f

            // 2. Calculate the new, larger dimensions for our virtual "poster".
            val targetWidth = viewWidth * scale
            val targetHeight = viewHeight * scale

            // 3. Define the top-left corner (x, y) in view coordinates.
            // Let's place the video's top-left corner in the CENTER of the screen.
            // This means x will be half the screen width, and y half the screen height.
            // A large portion will be pushed out to the right and bottom.
            //val targetX = viewWidth / 2f
            //val targetY = viewHeight / 2f

            // Example for shifting HALF of the video off the LEFT edge:
            val targetX = -targetWidth / 2f
            val targetY = 0f

            // 4. Create the RectF and send it to the renderer.
            val rect = RectF(
                targetX,
                targetY,
                targetX + targetWidth,
                targetY + targetHeight
            )
            videoRenderer.setVideoRect(rect)
        }
    }


    override fun onPause() {
        super.onPause()
        glSurfaceView.onPause()
        videoRenderer.mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView.onResume()
        videoRenderer.mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        videoRenderer.cleanup()
    }
}