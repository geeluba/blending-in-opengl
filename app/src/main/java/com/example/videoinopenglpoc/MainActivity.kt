package com.example.videoinopenglpoc

import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
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
                            removeVideoView()
                            addVideoView()
                        }

                        DisplayMode.None -> {

                        }
                    }

                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
                    // Do nothing
                }
            }
    }

    private fun addVideoView() {
        Log.d(TAG, "addVideoView")

        //init glSurfaceView and VideoRenderer
        glSurfaceView = GLSurfaceView(this)
        //tried below, seems not necessary
        // 1. We need a surface with an Alpha channel. 8 bits for RGBA.
        //glSurfaceView?.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        // 2. Tell the system that this surface is going to be drawn on top of other things.
        //glSurfaceView?.setZOrderOnTop(true)
        // 3. Tell the SurfaceHolder to create a translucent surface.
        //glSurfaceView?.holder?.setFormat(PixelFormat.TRANSLUCENT)

        glSurfaceView?.setEGLContextClientVersion(2)
        videoRenderer = VideoRenderer(this, glSurfaceView!!)
        glSurfaceView?.setRenderer(videoRenderer)
        glSurfaceView?.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

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
            gv.y = (screenHeight - availableHeight).toFloat()
            gv.y = (screenHeight - availableHeight).toFloat()
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
            fl.y = (screenHeight - availableHeight).toFloat()
            fl.background = getDrawable(R.drawable.rectangular_frame)
        }

        //compute the virtual combined rectF
        val overlappingWidth = getOverlappingWidth(availableWidth)
        Log.d(TAG, "overlappingWidth=$overlappingWidth")
        val totalWidth = availableWidth * 2 - overlappingWidth
        virtualCombinedRectF = RectF(
            currentDisplayMode.let {
                when (it) {
                    leftHalf -> 0f - overlappingWidth / 2
                    else -> 0f - totalWidth / 2f + overlappingWidth / 2
                }
            },
            0f,
            currentDisplayMode.let {
                when (it) {
                    leftHalf -> totalWidth.toFloat() + overlappingWidth / 2f
                    else -> totalWidth / 2f + overlappingWidth / 2f
                }
            },
            availableHeight.toFloat()
        )
        Log.d(TAG, "virtualCombinedRectF=$virtualCombinedRectF")
        videoRenderer?.setVideoRect(virtualCombinedRectF)
        val rectWidth = availableWidth / 2f
        val rectHeight = availableHeight / 2f

        val rectLeft = (availableWidth - rectWidth) / 2f
        val rectTop = (availableHeight - rectHeight) / 2f

        val blendingRectF = RectF(
            rectLeft,
            rectTop,
            rectLeft + rectWidth,
            rectTop + rectHeight
        )
        videoRenderer?.setBlendRect(blendingRectF, 0.8f)
        Log.d(TAG, "blendingRectF=$blendingRectF")
    }

    private fun removeVideoView() {
        Log.d(TAG, "removeVideoView")

        glSurfaceView?.let { gv ->
            gv.onPause()
            gv.queueEvent {
                videoRenderer?.cleanup()
            }
            binding.root.removeView(gv)
            glSurfaceView = null
            videoRenderer = null
        }
        frameLayout?.let { fl ->
            binding.root.removeView(fl)
            frameLayout = null
        }
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
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        private val singleProjectorAspectRatio = 12f / 9f
        private val singleProjectorOverlappingRatio = 3f / 12f
    }
}