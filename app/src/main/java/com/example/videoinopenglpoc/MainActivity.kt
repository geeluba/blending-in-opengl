package com.example.videoinopenglpoc

import android.graphics.PixelFormat
import android.graphics.RectF
import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import com.example.videoinopenglpoc.MainActivity.DisplayMode.leftHalf
import com.example.videoinopenglpoc.MainActivity.DisplayMode.rightHalf
import com.example.videoinopenglpoc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private val TAG = "===rode===MainActivity"

    private lateinit var binding: ActivityMainBinding
    private var glSurfaceView: GLSurfaceView? = null
    private var frameLayout: FrameLayout? = null
    private var videoRenderer: VideoRenderer? = null

    private var imageRenderer: ImageRenderer? = null
    //physical displayed area on wall
    private var virtualCombinedRectF = RectF(0f, 0f, 200f, 200f)

    enum class DisplayMode {
        None,
        leftHalf,
        rightHalf
    }

    private var currentDisplayMode = DisplayMode.None

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideSystemUi()

        initSpinner()
    }

    private fun initSpinner() {
        val spinner = findViewById<android.widget.Spinner>(R.id.spinner)
        val items = listOf(DisplayMode.None, leftHalf, DisplayMode.rightHalf)

        val adapter = ArrayAdapter(this, R.layout.spinner_item, items)
        spinner.adapter = adapter
        spinner.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val selectedItem = parent?.getItemAtPosition(position).toString()
                    Log.d(TAG, "Spinner selected item: $selectedItem")
                    currentDisplayMode = parent?.getItemAtPosition(position) as DisplayMode

                    when (currentDisplayMode) {

                        leftHalf, rightHalf -> {
                            removeRenderView()
                            addRenderView()
                        }

                        DisplayMode.None -> {
                            removeRenderView()
                        }
                    }

                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    // Do nothing
                }
            }
    }

    private fun addRenderView() {
        Log.d(TAG, "addRenderView+++")

        //init glSurfaceView and VideoRenderer/ImageRenderer
        glSurfaceView = GLSurfaceView(this)
        glSurfaceView?.setEGLContextClientVersion(2)
        //tried below, seems not necessary
        // 1. We need a surface with an Alpha channel. 8 bits for RGBA.
        //glSurfaceView?.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        // 2. Tell the system that this surface is going to be drawn on top of other things.
        //glSurfaceView?.setZOrderOnTop(true)
        // 3. Tell the SurfaceHolder to create a translucent surface.
        //glSurfaceView?.holder?.setFormat(PixelFormat.TRANSLUCENT)

        if (loadIamge) {
            imageRenderer = ImageRenderer(this, glSurfaceView!!)
            glSurfaceView?.setRenderer(imageRenderer)
            glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        } else {
            videoRenderer = VideoRenderer(this, glSurfaceView!!)
            glSurfaceView?.setRenderer(videoRenderer)
            glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        //init frameLayout
        frameLayout = FrameLayout(this)

        val (screenWidth, screenHeight) = getScreenResolution()

        val (availableWidth, availableHeight) = getLargestAvailableScreenFromRatio(
            screenWidth,
            screenHeight,
            singleProjectorAspectRatio
        )
        Log.d(
            TAG,
            "Original Screen: width = ${screenWidth}, height=${screenHeight} and largest available screen: width=$availableWidth, height=$availableHeight"
        )

        glSurfaceView?.let { gv ->
            binding.root.addView(gv)
            gv.layoutParams.width = availableWidth
            gv.layoutParams.height = availableHeight
            gv.x = currentDisplayMode.let {
                when (it) {
                    leftHalf ->(screenWidth - availableWidth).toFloat()
                    else -> 0f
                }
            }
            gv.y = (screenHeight - availableHeight).toFloat() / 2f // Center vertically
            //tried below, seems not necessary
            //gv.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
            //gv.background = null
            Log.d(TAG, "glSurfaceView x=${glSurfaceView!!.x}, y=${glSurfaceView!!.y}")
        }

        frameLayout?.let { fl ->
            binding.root.addView(fl)
            fl.layoutParams.width = availableWidth
            fl.layoutParams.height = availableHeight
            fl.x = currentDisplayMode.let {
                when (it) {
                    leftHalf ->(screenWidth - availableWidth).toFloat()
                    else -> 0f
                }
            }
            fl.y = (screenHeight - availableHeight).toFloat() / 2f // Center vertically
            fl.background = getDrawable(R.drawable.rectangular_frame)
        }

        // --- CORRECTED CALCULATION for virtualCombinedRectF ---
        // The goal is to define a targetRect that, when processed by the renderer's
        // updateMVPMatrix function, results in the correct scaling and translation
        // to show only the relevant portion of the full image.
        val overlappingWidth = getOverlappingWidth(availableWidth)
        val totalWidth = (availableWidth * 2 - overlappingWidth).toFloat()
        Log.d(TAG, "totalWidth=$totalWidth, overlappingWidth=$overlappingWidth")

        // We work backwards from the required matrix transformation:
        // 1. The required horizontal scale is (totalWidth / availableWidth).
        //    The renderer calculates scale as `targetRect.width() / availableWidth`.
        //    Therefore, `targetRect.width()` must be `totalWidth`.
        //
        // 2. The required horizontal translation is different for left and right projectors.
        //    The renderer calculates translation based on `targetRect.centerX()`.
        //    We can calculate the `centerX` needed to produce the correct translation.
        virtualCombinedRectF = when (currentDisplayMode) {
            leftHalf -> {
                // For the left projector, we need to shift the scaled image to the right
                // so that its left edge aligns with the screen's left edge.
                // This requires a centerX of `totalWidth / 2f`.
                val centerX = totalWidth / 2f
                RectF(centerX - totalWidth / 2f, 0f, centerX + totalWidth / 2f, availableHeight.toFloat())
            }
            rightHalf -> {
                // For the right projector, we need to shift the scaled image to the left
                // so its right edge aligns with the screen's right edge.
                // This requires a centerX of `availableWidth - (totalWidth / 2f)`.
                val centerX = availableWidth - (totalWidth / 2f)
                val left = centerX - totalWidth / 2f
                val right = centerX + totalWidth / 2f
                RectF(left, 0f, right, availableHeight.toFloat())
            }
            else -> {
                // Default case for 'None'
                RectF(0f, 0f, availableWidth.toFloat(), availableHeight.toFloat())
            }
        }

        Log.d(TAG, "virtualCombinedRectF for $currentDisplayMode is $virtualCombinedRectF")

        if (loadIamge) {
            imageRenderer?.setImageRect(virtualCombinedRectF)
        } else {
            videoRenderer?.setVideoRect(virtualCombinedRectF)
        }


        val gamma = 2.2f
        val alpha = 1.0f

        //overlapping area on screen
        val blendRecF = RectF(
            when (currentDisplayMode) {
                leftHalf -> (availableWidth - overlappingWidth) * 1f
                else -> 0f
            },
            0f,
            when (currentDisplayMode) {
                leftHalf -> availableWidth * 1f
                else -> overlappingWidth * 1f
            },
            availableHeight.toFloat()
        )

        if (loadIamge) {
            imageRenderer?.setBlendConfig(
                isLeft = currentDisplayMode == leftHalf,
                blendRect = blendRecF,
                gamma = gamma,
                alpha = alpha
            )
        } else {
            videoRenderer?.setBlendConfig(
                isLeft = currentDisplayMode == leftHalf,
                blendRect = blendRecF,
                gamma = gamma,
                alpha = alpha
            )
        }
        Log.d(TAG, "blendingRectF=$blendRecF")
        Log.d(TAG, "addRenderView---")
    }

    private fun removeRenderView() {
        Log.d(TAG, "removeRenderView+++")

        glSurfaceView?.let { gv ->
            gv.onPause()
            gv.queueEvent {
                if (loadIamge) {
                    imageRenderer?.cleanup()
                } else {
                    videoRenderer?.cleanup()
                }
            }
            binding.root.removeView(gv)
            glSurfaceView = null
            videoRenderer = null
            imageRenderer = null
        }
        frameLayout?.let { fl ->
            binding.root.removeView(fl)
            frameLayout = null
        }
        Log.d(TAG, "removeRenderView---")
    }

    private fun hideSystemUi() {
        val decorView = window.decorView
        decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }


    private fun getScreenResolution(): Pair<Int, Int> {
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        var screen: Pair<Int, Int>
        try {
            val method = Display::class.java.getMethod("getRealMetrics", DisplayMetrics::class.java)
            method.invoke(display, metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            screen = Pair(width, height)
        } catch (e: Exception) {
            // Fallback
            display.getMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            screen = Pair(width, height)
            Log.d(TAG, "screen width=$width, height=$height (fallback, may exclude nav bar)")
        }
        return screen
    }

    private fun getLargestAvailableScreenFromRatio(
        screenWidth: Int,
        screenHeight: Int,
        ratio: Float,
    ): Pair<Int, Int> {
        val targetAspectRatio = ratio
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

        return if (screenAspectRatio > targetAspectRatio) {
            // 屏幕更宽，高度受限
            val availableWidth = (screenHeight * targetAspectRatio).toInt()
            Pair(availableWidth, screenHeight)
        } else {
            // 屏幕更高，宽度受限
            val availableHeight = (screenWidth / targetAspectRatio).toInt()
            Pair(screenWidth, availableHeight)
        }
    }

    private fun getOverlappingWidth(totalWidth: Int): Int {
        return (totalWidth * singleProjectorOverlappingRatio).toInt()
    }

    override fun onPause() {
        super.onPause()
        glSurfaceView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        glSurfaceView?.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeRenderView()
    }

    companion object {

        private const val loadIamge = true    //otherwise load video
        private const val singleProjectorAspectRatio = 12f / 9f
        private const val singleProjectorOverlappingRatio = 3f / 12f
    }
}