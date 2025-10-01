package com.example.videoinopenglpoc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.div
import kotlin.text.set

class ImageRenderer(private val context: Context, private val glSurfaceView: GLSurfaceView) :
    GLSurfaceView.Renderer {

    private val TAG = "===rode===ImageRenderer"

    // Edge blending configuration (simplified)
    data class SrgbEdgeBlendConfig(
        val rect: RectF,
        val alpha: Float = 0.5f,
        val mode: BlendMode = BlendMode.NONE
    )

    enum class BlendMode(val value: Int) {
        NONE(0),
        LEFT_EDGE(1),
        RIGHT_EDGE(2)
    }

    private var programHandle: Int = 0
    private var imageTextureId: Int = 0

    // Shader attribute and uniform locations
    private var aPositionLocation: Int = 0
    private var aTexCoordLocation: Int = 0
    private var uMVPMatrixLocation: Int = 0
    private var sTextureLocation: Int = 0

    private var uIsLeftLocation: Int = 0

    private var uBlendInvWidthLocation: Int = 0

    // Buffers for vertex and texture coordinates
    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    // Matrices
    private val mvpMatrix = FloatArray(16)

    // Store the image's dimensions
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // 保存 GLSurfaceView 的尺寸
    private var viewWidth: Int = 0
    private var viewHeight: Int = 0

    // 保存目标位置和大小 (View 座标)
    private var targetRect = RectF(0f, 0f, 0f, 0f)

    private val vertices = floatArrayOf(
        -1.0f, -1.0f, 0.0f, // Bottom-left
        1.0f, -1.0f, 0.0f, // Bottom-right
        -1.0f, 1.0f, 0.0f, // Top-left
        1.0f, 1.0f, 0.0f  // Top-right
    )

    private val texCoords = floatArrayOf(
        0.0f, 1.0f, // Bottom-left (flipped Y)
        1.0f, 1.0f, // Bottom-right (flipped Y)
        0.0f, 0.0f, // Top-left (flipped Y)
        1.0f, 0.0f  // Top-right (flipped Y)
    )

    // Blending properties
    private var uBlendRectLocation: Int = 0
    private var uAlphaLocation: Int = 0
    //private var uBlendAlphaLocation: Int = 0
    private val blendRectNormalized = floatArrayOf(0f, 0f, 0f, 0f)


    private var uResolutionLocation: Int = 0
    private val resolution = floatArrayOf(0f, 0f)

    @Volatile
    private var isSurfaceReady = false
    private var blendAlpha = 1.0f
    private var isLeft: Boolean = true

    private var precomputedInvWidth = 0f

    private var pendingSrgbEdgeBlendConfig: SrgbEdgeBlendConfig? = null

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

    fun setImageRect(rect: RectF) {
        glSurfaceView.queueEvent {
            targetRect = rect
            updateMVPMatrix()
            glSurfaceView.requestRender()
        }
    }

    private fun updateMVPMatrix() {
        if (viewWidth == 0 || viewHeight == 0 || imageWidth == 0 || imageHeight == 0) {
            return
        }

        val imageAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
        val targetAspectRatio = targetRect.width() / targetRect.height()

        var finalWidth = targetRect.width()
        var finalHeight = targetRect.height()

        if (imageAspectRatio > targetAspectRatio) {
            finalHeight = finalWidth / imageAspectRatio
        } else {
            finalWidth = finalHeight * imageAspectRatio
        }

        val scaleX = finalWidth / viewWidth
        val scaleY = finalHeight / viewHeight

        val targetCenterX_view = targetRect.left + targetRect.width() / 2.0f
        val targetCenterY_view = targetRect.top + targetRect.height() / 2.0f

        val translateX_ndc = (targetCenterX_view / viewWidth) * 2.0f - 1.0f
        val translateY_ndc = -((targetCenterY_view / viewHeight) * 2.0f - 1.0f)

        Matrix.setIdentityM(mvpMatrix, 0)
        Matrix.translateM(mvpMatrix, 0, translateX_ndc, translateY_ndc, 0f)
        Matrix.scaleM(mvpMatrix, 0, scaleX, scaleY, 1f)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.d(TAG, "onSurfaceCreated")

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Load and compile shaders
        val vertexShaderSource = readShaderFromAssets("image_vertex_shader.glsl")
        val fragmentShaderSource = readShaderFromAssets("image_fragment_shader.glsl")
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexShaderSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderSource)
        programHandle = createAndLinkProgram(vertexShader, fragmentShader)

        // Get shader variable locations
        aPositionLocation = GLES20.glGetAttribLocation(programHandle, "aPosition")
        aTexCoordLocation = GLES20.glGetAttribLocation(programHandle, "aTexCoord")
        uMVPMatrixLocation = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
        sTextureLocation = GLES20.glGetUniformLocation(programHandle, "sTexture")
        uIsLeftLocation = GLES20.glGetUniformLocation(programHandle, "uIsLeftFlag")
        uBlendRectLocation = GLES20.glGetUniformLocation(programHandle, "uBlendRect")
        uBlendInvWidthLocation = GLES20.glGetUniformLocation(programHandle, "uBlendInvWidth")
        uAlphaLocation = GLES20.glGetUniformLocation(programHandle, "uAlpha")
        uResolutionLocation = GLES20.glGetUniformLocation(programHandle, "uResolution")


        // Load image and create texture
        loadImageTexture()

        Matrix.setIdentityM(mvpMatrix, 0)
    }

    private fun loadImageTexture() {
        // Load image from resources
        val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.tiger)
            ?: throw IOException("Failed to load image resource")

        imageWidth = bitmap.width
        imageHeight = bitmap.height
        Log.d(TAG, "Image dimensions: ${imageWidth}x${imageHeight}")

        //this images loading works faster, but it puts images in assets folder which is designed to hold the opengl
        // shaders and other gl related files
        // hold the thoughts and dig into it later
        /*var bitmap: Bitmap
        val assetManager = context.assets
        assetManager.open("temple.jpg").use { inputStream ->
            bitmap = BitmapFactory.decodeStream(inputStream)
            imageWidth = bitmap.width
            imageHeight = bitmap.height
            Log.d(TAG, "Image dimensions: ===${imageWidth}x${imageHeight}===")
        }*/


        // Generate and bind texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        imageTextureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )

        // Upload bitmap to texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // Recycle bitmap
        bitmap.recycle()

        // Trigger matrix update
        glSurfaceView.queueEvent { updateMVPMatrix() }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: $width x $height")
        GLES20.glViewport(0, 0, width, height)
        this.viewWidth = width
        this.viewHeight = height

        resolution[0] = width.toFloat()
        resolution[1] = height.toFloat()

        if (targetRect.width() == 0f || targetRect.height() == 0f) {
            targetRect.set(0f, 0f, width.toFloat(), height.toFloat())
        }

        isSurfaceReady = true
        if (pendingSrgbEdgeBlendConfig != null) {
            updateSrgbEdgeBlendConfig(pendingSrgbEdgeBlendConfig!!)
            pendingSrgbEdgeBlendConfig = null
        }


        updateMVPMatrix()
    }

    // Backward compatibility method with sRGB handling
    fun setBlendConfig(isLeft: Boolean, blendRect: RectF, gamma: Float, alpha: Float) {
        // Note: gamma parameter is ignored as we use fixed sRGB conversion
        val mode = if (isLeft) BlendMode.RIGHT_EDGE else BlendMode.LEFT_EDGE
        val config = SrgbEdgeBlendConfig(blendRect, alpha, mode)
        configureSrgbEdgeBlending(config)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        GLES20.glUseProgram(programHandle)

        // Enable and pass vertex data
        GLES20.glEnableVertexAttribArray(aPositionLocation)
        GLES20.glVertexAttribPointer(aPositionLocation, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)

        // Enable and pass texture coordinate data
        GLES20.glEnableVertexAttribArray(aTexCoordLocation)
        GLES20.glVertexAttribPointer(
            aTexCoordLocation,
            2,
            GLES20.GL_FLOAT,
            false,
            8,
            texCoordBuffer
        )

        // Pass matrices
        GLES20.glUniformMatrix4fv(uMVPMatrixLocation, 1, false, mvpMatrix, 0)

        // Pass blending uniforms
        GLES20.glUniform2fv(uResolutionLocation, 1, resolution, 0)
        GLES20.glUniform1f(uIsLeftLocation, if (isLeft) 1f else 0f)
        Log.d(TAG, "onDrawFrame: isLeft=$isLeft")
        GLES20.glUniform1f(uBlendInvWidthLocation, precomputedInvWidth)
        GLES20.glUniform4fv(uBlendRectLocation, 1, blendRectNormalized, 0)
        GLES20.glUniform1f(uAlphaLocation, blendAlpha)

        // Bind texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, imageTextureId)
        GLES20.glUniform1i(sTextureLocation, 0)

        // Draw rectangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLocation)
        GLES20.glDisableVertexAttribArray(aTexCoordLocation)
    }

    // New sRGB-aware edge blending configuration
    fun configureSrgbEdgeBlending(config: SrgbEdgeBlendConfig) {
        if (!isSurfaceReady) {
            pendingSrgbEdgeBlendConfig = config
            return
        }

        glSurfaceView.queueEvent {
            updateSrgbEdgeBlendConfig(config)
            glSurfaceView.requestRender()
        }
    }

    private fun updateSrgbEdgeBlendConfig(config: SrgbEdgeBlendConfig) {
        if (viewWidth == 0 || viewHeight == 0) return
        blendRectNormalized[0] = config.rect.left / viewWidth
        blendRectNormalized[1] = 1f - config.rect.bottom / viewHeight
        blendRectNormalized[2] = config.rect.right / viewWidth
        blendRectNormalized[3] = 1f - config.rect.top / viewHeight
        val widthN = blendRectNormalized[2] - blendRectNormalized[0]
        // 避免除零
        val inv = if (widthN > 0f) 1f / widthN else 0f
        precomputedInvWidth = inv
        blendAlpha = config.alpha
        isLeft = when (config.mode) {
            BlendMode.LEFT_EDGE -> true
            BlendMode.RIGHT_EDGE -> false
            else -> true
        }
        Log.d(TAG, "Updated blend config: rect=${config.rect}, alpha=${config.alpha}, mode=${config.mode}")
        Log.d(TAG, "isLeft=$isLeft, precomputedInvWidth=$precomputedInvWidth")
    }



    // Helper functions
    private fun readShaderFromAssets(fileName: String): String {
        return context.assets.open(fileName).bufferedReader().use { it.readText() }
    }

    private fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader))
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
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e(TAG, "Program link failed: " + GLES20.glGetProgramInfoLog(program))
            GLES20.glDeleteProgram(program)
            return 0
        }
        return program
    }

    fun cleanup() {
        if (imageTextureId != 0) {
            val textures = intArrayOf(imageTextureId)
            GLES20.glDeleteTextures(1, textures, 0)
            imageTextureId = 0
        }
        if (programHandle != 0) GLES20.glDeleteProgram(programHandle)
        programHandle = 0
    }
}