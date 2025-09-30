package com.example.videoinopenglpoc

import android.content.Context
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class VideoRenderer(private val context: Context, private val glSurfaceView: GLSurfaceView) :
    GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    private val TAG = "===rode===VideoRenderer"

    private lateinit var surfaceTexture: SurfaceTexture
    var mediaPlayer: MediaPlayer? = null

    private var programHandle: Int = 0
    private var videoTextureId: Int = 0

    // Shader attribute and uniform locations
    private var aPositionLocation: Int = 0
    private var aTexCoordLocation: Int = 0
    private var uMVPMatrixLocation: Int = 0
    private var uTexMatrixLocation: Int = 0
    private var sTextureLocation: Int = 0

    // Buffers for vertex and texture coordinates
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    // Matrices
    private val mvpMatrix = FloatArray(16)
    private val textureTransformMatrix = FloatArray(16)

    @Volatile
    private var frameAvailable = false

    // Store the video's native dimensions
    private var videoWidth: Int = 1
    private var videoHeight: Int = 1


    // --- 新增屬性 ---
    // 保存 GLSurfaceView 的尺寸
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    // 保存目標位置和大小 (View 座標)
    private var targetRect = RectF(0f, 0f, 0f, 0f)

    private val vertices = floatArrayOf(
        // x,  y, z
        -1.0f, -1.0f, 0.0f, // Bottom-left
        1.0f, -1.0f, 0.0f, // Bottom-right
        -1.0f, 1.0f, 0.0f, // Top-left
        1.0f, 1.0f, 0.0f  // Top-right
    )

    private val texCoords = floatArrayOf(
        // u, v
        0.0f, 0.0f, // Bottom-left
        1.0f, 0.0f, // Bottom-right
        0.0f, 1.0f, // Top-left
        1.0f, 1.0f  // Top-right
    )

    // --- NEW PROPERTIES FOR BLENDING ---
    private var uBlendRectLocation: Int = 0
    private var uIsLeftLocation: Int = 0
    private var uGammaLocation: Int = 0
    private var uAlphaLocation: Int = 0

    // (minX, minY, maxX, maxY) in normalized screen coordinates (Y is up)
    private val blendRectNormalized = floatArrayOf(0f, 0f, 0f, 0f)
    private var uResolutionLocation: Int = 0
    private val resolution = floatArrayOf(0f, 0f)

    @Volatile
    private var isSurfaceReady = false
    private var pendingBlendRect: RectF? = null
    private var blendAlpha = 1.0f
    private var pendingBlendAlpha: Float = 1.0f
    private var blendGamma: Float = 1.0f
    private var pendingBlendGamma: Float = 1.0f
    private var isLeft: Boolean = true
    private var pendingisLeft: Boolean = true

    init {
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices)
        vertexBuffer.position(0)

        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(texCoords)
        texCoordBuffer.position(0)
    }

    // --- NEW PUBLIC METHOD ---
    /**
     * 從外部(UI線程)設定影片的顯示位置和大小
     * @param rect 目標矩形，使用 View 座標 (左上角為 (0,0))
     */
    fun setVideoRect(rect: RectF) {
        // 使用 queueEvent 將操作發送到 GL 線程，確保線程安全
        glSurfaceView.queueEvent {
            targetRect = rect
            updateMVPMatrix()
            glSurfaceView.requestRender() // 更新矩陣後請求重新繪製
        }
    }

    /**
     * 封裝的矩陣計算邏輯
     */
    /**
     * Calculates the MVP matrix, now with aspect ratio correction.
     */
    private fun updateMVPMatrix() {
        if (viewWidth == 0 || viewHeight == 0 || videoWidth == 0 || videoHeight == 0) {
            return
        }

        // 1. Calculate aspect ratios
        val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val targetAspectRatio = targetRect.width() / targetRect.height()

        var finalWidth = targetRect.width()
        var finalHeight = targetRect.height()

        // 2. Compare ratios to determine final render dimensions (fit inside logic)
        if (videoAspectRatio > targetAspectRatio) {
            // Video is wider than the target area (letterbox).
            // The width should match the target, and the height is scaled down.
            finalHeight = finalWidth / videoAspectRatio
        } else {
            // Video is taller or has the same aspect ratio as the target area (pillarbox).
            // The height should match the target, and the width is scaled down.
            finalWidth = finalHeight * videoAspectRatio
        }

        // 3. Calculate scale based on the *corrected* dimensions
        val scaleX = finalWidth / viewWidth
        val scaleY = finalHeight / viewHeight
        Log.d(TAG, "viewWidth=$viewWidth, viewHeight=$viewHeight, finalWidth=$finalWidth, finalHeight=$finalHeight")

        Log.d(TAG, "scaleX=$scaleX, scaleY=$scaleY")

        // 4. Calculate translation to center the corrected video inside the original targetRect
        val targetCenterX_view = targetRect.left + targetRect.width() / 2.0f
        val targetCenterY_view = targetRect.top + targetRect.height() / 2.0f
        Log.d(TAG, "targetCenterX_view=$targetCenterX_view, targetCenterY_view=$targetCenterY_view")

        val translateX_ndc = (targetCenterX_view / viewWidth) * 2.0f - 1.0f
        val translateY_ndc = -((targetCenterY_view / viewHeight) * 2.0f - 1.0f)
        Log.d(TAG, "translateX_ndc=$translateX_ndc, translateY_ndc=$translateY_ndc")

        // 5. Build the final matrix
        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.translateM(mvpMatrix, 0, translateX_ndc, translateY_ndc, 0f)
        Matrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1f)
    }


    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated")
        // --- ENABLE BLENDING ---
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        // 1. 載入並編譯 Shaders
        val vertexShaderSource = readShaderFromAssets("video_vertex_shader.glsl")
        val fragmentShaderSource = readShaderFromAssets("video_fragment_shader.glsl")
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        programHandle = createAndLinkProgram(vertexShader, fragmentShader)

        // 2. 獲取 Shader 中的變數位置
        aPositionLocation = GLES20.glGetAttribLocation(programHandle, "aPosition")
        aTexCoordLocation = GLES20.glGetAttribLocation(programHandle, "aTexCoord")
        uMVPMatrixLocation = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
        uTexMatrixLocation = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
        sTextureLocation = GLES20.glGetUniformLocation(programHandle, "sTexture")
        uIsLeftLocation = GLES20.glGetUniformLocation(programHandle, "uIsLeft")
        uBlendRectLocation = GLES20.glGetUniformLocation(programHandle, "uBlendRect")
        uGammaLocation = GLES20.glGetUniformLocation(programHandle, "uGamma")
        uAlphaLocation = GLES20.glGetUniformLocation(programHandle, "uAlpha")
        uResolutionLocation = GLES20.glGetUniformLocation(programHandle, "uResolution")


        // 3. 建立 OpenGL 紋理並綁定到 SurfaceTexture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        videoTextureId = textures[0]

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_NEAREST
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        surfaceTexture = SurfaceTexture(videoTextureId)
        surfaceTexture.setOnFrameAvailableListener(this)

        // 4. 初始化 MediaPlayer 並將 SurfaceTexture 作為其輸出
        val surface = Surface(surfaceTexture)
        mediaPlayer = MediaPlayer()
        try {
            val afd = context.resources.openRawResourceFd(R.raw.cat)
            //val afd = context.resources.openRawResourceFd(R.raw.microscope)
            //val afd = context.resources.openRawResourceFd(R.raw.skynight)
            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mediaPlayer?.setSurface(surface)
            surface.release()
            mediaPlayer?.isLooping = true
            mediaPlayer?.prepareAsync()
            mediaPlayer?.setOnPreparedListener { mp ->

                // NEW: Get the native video dimensions
                this.videoWidth = mp.videoWidth
                this.videoHeight = mp.videoHeight

                // Trigger a matrix update now that we have the correct aspect ratio
                glSurfaceView.queueEvent { updateMVPMatrix() }

                mp.start()
            }
        } catch (e: IOException) {
            Log.e("VideoRenderer", "MediaPlayer setup failed", e)
        }

        Matrix.setIdentityM(mvpMatrix, 0)
    }

    //GLSurfaceView.Renderer
    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: $width x $height")
        GLES20.glViewport(0, 0, width, height)
        this.viewWidth = width
        this.viewHeight = height

        resolution[0] = width.toFloat()
        resolution[1] = height.toFloat()

        // 首次進入時，設定影片為全螢幕
        if (targetRect.width() == 0f || targetRect.height() == 0f) {
            targetRect.set(0f, 0f, width.toFloat(), height.toFloat())
        }

       isSurfaceReady = true
        if (pendingBlendRect != null) {
            updateBlendConfig(pendingisLeft, pendingBlendRect!!, pendingBlendGamma, pendingBlendAlpha)
            // Clear the pending request so it doesn't get applied again
            pendingBlendRect = null
        }

        // 更新 MVP 矩陣
        updateMVPMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(this) {
            if (frameAvailable) {
                surfaceTexture.updateTexImage()
                surfaceTexture.getTransformMatrix(textureTransformMatrix)
                frameAvailable = false
            }
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        //tried below, seems not necessary
        //GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)


        GLES20.glUseProgram(programHandle)

        // 啟用並傳遞頂點數據
        GLES20.glEnableVertexAttribArray(aPositionLocation)
        GLES20.glVertexAttribPointer(aPositionLocation, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        // 啟用並傳遞紋理座標數據
        GLES20.glEnableVertexAttribArray(aTexCoordLocation)
        GLES20.glVertexAttribPointer(
            aTexCoordLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            8,
            texCoordBuffer
        )

        // 傳遞矩陣
        GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uTexMatrixLocation, 1, false, textureTransformMatrix, 0)

        // --- Pass blending uniforms to the shader ---
        GLES20.glUniform2fv(uResolutionLocation, 1, resolution, 0)
        GLES20.glUniform1i(uIsLeftLocation, if (isLeft) 1 else 0)
        GLES20.glUniform4fv(uBlendRectLocation, 1, blendRectNormalized, 0)
        GLES20.glUniform1f(uGammaLocation, blendGamma)
        GLES20.glUniform1f(uAlphaLocation, blendAlpha)

        // 綁定紋理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTextureId)
        GLES20.glUniform1i(sTextureLocation, 0)

        // 繪製矩形
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLocation)
        GLES20.glDisableVertexAttribArray(aTexCoordLocation)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        synchronized(this) {
            frameAvailable = true
            // 通知 GLSurfaceView 有新的幀需要渲染
            glSurfaceView.requestRender()
        }
    }

    // Helper functions for shader compilation
    private fun readShaderFromAssets(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        // Check for compile errors
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e("VideoRenderer", "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createAndLinkProgram(vertexShader: Int, fragmentShader: Int): Int {
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        // Check for link errors
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e("VideoRenderer", "Program link failed: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    fun setBlendConfig(isLeft: Boolean, blendRect: RectF, gamma: Float, alpha: Float) {
        if (!isSurfaceReady) {
            Log.d(TAG, "Surface not ready, caching blend rect request")
            // Create a copy of the rect to avoid threading issues
            pendingBlendRect = if (blendRect != null) RectF(blendRect) else null
            pendingBlendAlpha = alpha
            pendingBlendGamma = gamma
            pendingisLeft = isLeft
            return
        }

        // If the surface IS ready, queue the event to run on the GL thread as before.
        glSurfaceView.queueEvent {
            updateBlendConfig(isLeft, blendRect, gamma, alpha)
            glSurfaceView.requestRender()
        }
    }

    private fun updateBlendConfig(isLeft: Boolean, blendRect: RectF, gamma: Float, alpha: Float) {
        glSurfaceView.queueEvent {
            this.isLeft = isLeft
            this.blendGamma = gamma
            this.blendAlpha = alpha
            if (viewWidth > 0 && viewHeight > 0) {
                blendRectNormalized[0] = blendRect.left / viewWidth
                blendRectNormalized[1] = 1.0f - blendRect.bottom / viewHeight
                blendRectNormalized[2] = blendRect.right / viewWidth
                blendRectNormalized[3] = 1.0f - blendRect.top / viewHeight
            }
            glSurfaceView.requestRender()
        }
    }

    fun cleanup() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        if (videoTextureId != 0) GLES20.glDeleteTextures(1, intArrayOf(videoTextureId), 0)
        if (programHandle != 0) GLES20.glDeleteProgram(programHandle)
        videoTextureId = 0
        programHandle = 0
    }
}