/*
 * Copyright 2018 Google Inc. All rights reserved.
 * Copyright 2021 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.thibaultbee.streampack.internal.gl

import android.content.Context
import android.graphics.*
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import io.github.thibaultbee.streampack.R
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer

/**
 * Optimized GL program for textured 2D shapes with overlay support.
 */
class OptimizedTexture2DProgram {

    // Data classes for better organization
    data class ShaderProgram(
        val handle: Int,
        val mvpMatrixLoc: Int,
        val positionLoc: Int,
        val textureCoordLoc: Int
    )

    data class TextureInfo(
        var id: Int = -1,
        var ratio: Float = 0f,
        var isLoading: Boolean = false,
        var bitmap: Bitmap? = null,
        var lastContent: String = ""
    )

    data class OverlayConfig(
        val scale: Float,
        val x: Float,
        val y: Float,
        val textureUnit: Int
    )

    // Shader programs
    private val mainProgram: ShaderProgram
    private val overlayPrograms = mutableMapOf<String, ShaderProgram>()

    // Texture management
    private val textures = mutableMapOf<String, TextureInfo>()

    // Configuration
    private val textScale = 0.08f
    private val startX = -0.98f
    private val startY = 0.8f
    private val spacing = 0.075f
    private val fixedScale = 0.6f

    // Coroutine scope for async operations
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        // Create main video program
        mainProgram = createShaderProgram("main", VERTEX_SHADER, FRAGMENT_SHADER_EXT)

        // Create overlay programs
        val overlayTypes = listOf("logo", "text1", "text2", "text3", "text4", "link1", "link2", "link3")
        overlayTypes.forEach { type ->
            overlayPrograms[type] = createShaderProgram(type, getVertexShader(type), getFragmentShader(type))
        }
    }

    private fun createShaderProgram(name: String, vertexSource: String, fragmentSource: String): ShaderProgram {
        val programHandle = createProgram(vertexSource, fragmentSource)
        if (programHandle == 0) {
            throw RuntimeException("Unable to create program for $name")
        }

        val positionAttr = "a${name.capitalize()}Position"
        val textureAttr = "a${name.capitalize()}TextureCoord"
        val mvpUniform = "u${name.capitalize()}MVPMatrix"

        return ShaderProgram(
            handle = programHandle,
            mvpMatrixLoc = GLES20.glGetUniformLocation(programHandle, mvpUniform),
            positionLoc = GLES20.glGetAttribLocation(programHandle, positionAttr),
            textureCoordLoc = GLES20.glGetAttribLocation(programHandle, textureAttr)
        ).also { program ->
            // Validate locations
            checkLocation(program.mvpMatrixLoc, mvpUniform)
            checkLocation(program.positionLoc, positionAttr)
            checkLocation(program.textureCoordLoc, textureAttr)
        }
    }

    fun release() {
        // Cancel any ongoing coroutines
        scope.cancel()

        // Delete programs
        GLES20.glDeleteProgram(mainProgram.handle)
        overlayPrograms.values.forEach { GLES20.glDeleteProgram(it.handle) }

        // Clean up textures
        textures.values.forEach { textureInfo ->
            if (textureInfo.id != -1) {
                GLES20.glDeleteTextures(1, intArrayOf(textureInfo.id), 0)
            }
            textureInfo.bitmap?.recycle()
        }
    }

    fun createTextureObject(): Int {
        val textureID = IntArray(1)
        GLES20.glGenTextures(1, textureID, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID[0])
        GlUtils.checkGlError("glBindTexture mTextureID")

        // Set texture parameters
        val params = mapOf(
            GLES20.GL_TEXTURE_MIN_FILTER to GLES20.GL_LINEAR,
            GLES20.GL_TEXTURE_MAG_FILTER to GLES20.GL_LINEAR,
            GLES20.GL_TEXTURE_WRAP_S to GLES20.GL_CLAMP_TO_EDGE,
            GLES20.GL_TEXTURE_WRAP_T to GLES20.GL_CLAMP_TO_EDGE
        )

        params.forEach { (param, value) ->
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, param, value)
        }

        GlUtils.checkGlError("glTexParameter")
        return textureID[0]
    }

    fun draw(
        context: Context,
        mvpMatrix: FloatArray,
        vertexBuffer: FloatBuffer,
        logoVertexBuffer: FloatBuffer,
        firstVertex: Int,
        vertexCount: Int,
        coordsPerVertex: Int,
        vertexStride: Int,
        texMatrix: FloatArray,
        texBuffer: FloatBuffer,
        logoTexBuffer: FloatBuffer,
        textureId: Int,
        texStride: Int,
        textVertexBuffer: FloatBuffer,
        textTexBuffer: FloatBuffer,
        text2VertexBuffer: FloatBuffer,
        text2TexBuffer: FloatBuffer,
        text3VertexBuffer: FloatBuffer,
        text3TexBuffer: FloatBuffer
    ) {
        GlUtils.checkGlError("draw start")

        // 1. Draw main video frame
        drawMainVideo(mvpMatrix, vertexBuffer, firstVertex, vertexCount, coordsPerVertex, vertexStride, texMatrix, texBuffer, textureId, texStride)

        // 2. Setup blending for overlays
        setupBlending()

        // 3. Draw overlays
        drawOverlays(context, logoVertexBuffer, logoTexBuffer, textVertexBuffer, textTexBuffer, text2VertexBuffer, text2TexBuffer, text3VertexBuffer, text3TexBuffer)

        // 4. Cleanup
        cleanup()
    }

    private fun drawMainVideo(
        mvpMatrix: FloatArray,
        vertexBuffer: FloatBuffer,
        firstVertex: Int,
        vertexCount: Int,
        coordsPerVertex: Int,
        vertexStride: Int,
        texMatrix: FloatArray,
        texBuffer: FloatBuffer,
        textureId: Int,
        texStride: Int
    ) {
        GLES20.glUseProgram(mainProgram.handle)

        // Set texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        // Set uniforms
        GLES20.glUniformMatrix4fv(mainProgram.mvpMatrixLoc, 1, false, mvpMatrix, 0)
        val texMatrixLoc = GLES20.glGetUniformLocation(mainProgram.handle, "uTexMatrix")
        GLES20.glUniformMatrix4fv(texMatrixLoc, 1, false, texMatrix, 0)

        // Set attributes
        GLES20.glEnableVertexAttribArray(mainProgram.positionLoc)
        GLES20.glVertexAttribPointer(mainProgram.positionLoc, coordsPerVertex, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)

        GLES20.glEnableVertexAttribArray(mainProgram.textureCoordLoc)
        GLES20.glVertexAttribPointer(mainProgram.textureCoordLoc, 2, GLES20.GL_FLOAT, false, texStride, texBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
    }

    private fun setupBlending() {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawOverlays(
        context: Context,
        logoVertexBuffer: FloatBuffer,
        logoTexBuffer: FloatBuffer,
        textVertexBuffer: FloatBuffer,
        textTexBuffer: FloatBuffer,
        text2VertexBuffer: FloatBuffer,
        text2TexBuffer: FloatBuffer,
        text3VertexBuffer: FloatBuffer,
        text3TexBuffer: FloatBuffer
    ) {
        // Draw logo
        drawLogo(context, logoVertexBuffer, logoTexBuffer)

        // Draw text overlays
        drawTextOverlay("text1", TEXT1, textVertexBuffer, textTexBuffer, 1, SCORE1, TURN1)
        drawTextOverlay("text2", TEXT2, text2VertexBuffer, text2TexBuffer, 2, SCORE2, TURN2)
        drawTextOverlay("text3", TEXT3, text3VertexBuffer, text3TexBuffer, 0)
        drawTextOverlay("text4", TEXT4, text3VertexBuffer, text3TexBuffer, 3)

        // Draw link overlays
        drawLinkOverlay("link1", LINK1, logoVertexBuffer, logoTexBuffer, 6)
        drawLinkOverlay("link2", LINK2, logoVertexBuffer, logoTexBuffer, 7)
        drawLinkOverlay("link3", LINK3, logoVertexBuffer, logoTexBuffer, 8)
    }

    private fun drawLogo(context: Context, vertexBuffer: FloatBuffer, texBuffer: FloatBuffer) {
        val textureInfo = textures.getOrPut("logo") { TextureInfo() }

        if (textureInfo.id == -1) {
            val (id, ratio) = getLogoResource(context, R.drawable.logo)
            textureInfo.id = id
            textureInfo.ratio = ratio
        }

        if (textureInfo.id != -1) {
            val config = OverlayConfig(0.2f, 0.85f, 0.85f, 1)
            drawOverlay("logo", textureInfo, vertexBuffer, texBuffer, config)
        }
    }

    private fun drawTextOverlay(
        type: String,
        text: String,
        vertexBuffer: FloatBuffer,
        texBuffer: FloatBuffer,
        textureUnit: Int,
        score: String? = null,
        turn: String? = null
    ) {
        if (text.isEmpty()) return

        val textureInfo = textures.getOrPut(type) { TextureInfo() }
        val contentKey = "$text|$score|$turn"

        if (textureInfo.id == -1 || textureInfo.lastContent != contentKey) {
            val maxLengthText = if (score != null) getMaxLengthText() else null
            val (id, ratio) = createTextTexture(text, 30f, Color.WHITE, maxLengthText, score, turn)
            textureInfo.id = id
            textureInfo.ratio = ratio
            textureInfo.lastContent = contentKey
        }

        if (textureInfo.id != -1) {
            val yOffset = when (type) {
                "text1" -> startY - spacing
                "text2" -> startY - (2 * spacing)
                "text3" -> startY
                "text4" -> startY - (3 * spacing)
                else -> startY
            }
            val config = OverlayConfig(textScale, startX, yOffset, textureUnit + 2)
            drawOverlay(type, textureInfo, vertexBuffer, texBuffer, config)
        }
    }

    private fun drawLinkOverlay(
        type: String,
        url: String,
        vertexBuffer: FloatBuffer,
        texBuffer: FloatBuffer,
        textureUnit: Int
    ) {
        if (url.isEmpty()) return

        val textureInfo = textures.getOrPut(type) { TextureInfo() }

        // Load bitmap asynchronously if needed
        if (!textureInfo.isLoading && (textureInfo.bitmap == null || textureInfo.lastContent != url)) {
            textureInfo.isLoading = true
            scope.launch {
                textureInfo.bitmap = loadBitmapFromUrl(url)
                textureInfo.isLoading = false
                textureInfo.lastContent = url
                textureInfo.id = -1 // Force texture recreation
            }
        }

        // Create texture from bitmap if available
        if (textureInfo.bitmap != null && textureInfo.id == -1) {
            val (id, ratio) = loadTextureFromBitmap(textureInfo.bitmap!!)
            textureInfo.id = id
            textureInfo.ratio = ratio
        }

        if (textureInfo.id != -1) {
            val config = when (type) {
                "link1" -> OverlayConfig(0.11f, 0.85f - 0.117f/2 - 0.05f, 0.85f, textureUnit)
                "link2" -> OverlayConfig(0.11f, startX, -0.85f, textureUnit)
                "link3" -> OverlayConfig(0.11f, 0.98f, -0.85f, textureUnit)
                else -> OverlayConfig(0.11f, 0f, 0f, textureUnit)
            }
            drawOverlay(type, textureInfo, vertexBuffer, texBuffer, config)
        }
    }

    private fun drawOverlay(
        type: String,
        textureInfo: TextureInfo,
        vertexBuffer: FloatBuffer,
        texBuffer: FloatBuffer,
        config: OverlayConfig
    ) {
        val program = overlayPrograms[type] ?: return

        GLES20.glUseProgram(program.handle)

        // Set texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + config.textureUnit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureInfo.id)

        val textureLoc = GLES20.glGetUniformLocation(program.handle, "sTexture")
        if (textureLoc != -1) {
            GLES20.glUniform1i(textureLoc, config.textureUnit)
        }

        // Calculate transformation matrix
        val mvpMatrix = FloatArray(16)
        Matrix.setIdentityM(mvpMatrix, 0)

        val horizontalScale = (config.scale * textureInfo.ratio) * fixedScale
        Matrix.translateM(mvpMatrix, 0, config.x, config.y, 0f)
        Matrix.scaleM(mvpMatrix, 0, horizontalScale, config.scale, 1f)

        GLES20.glUniformMatrix4fv(program.mvpMatrixLoc, 1, false, mvpMatrix, 0)

        // Set attributes
        GLES20.glEnableVertexAttribArray(program.positionLoc)
        GLES20.glVertexAttribPointer(program.positionLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(program.textureCoordLoc)
        GLES20.glVertexAttribPointer(program.textureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun cleanup() {
        GLES20.glDisable(GLES20.GL_BLEND)

        // Disable all vertex attribute arrays
        val allPrograms = listOf(mainProgram) + overlayPrograms.values
        allPrograms.forEach { program ->
            GLES20.glDisableVertexAttribArray(program.positionLoc)
            GLES20.glDisableVertexAttribArray(program.textureCoordLoc)
        }

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    // Helper methods (simplified versions of original methods)
    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) return 0

        val program = GLES20.glCreateProgram()
        GlUtils.checkGlError("glCreateProgram")

        if (program == 0) throw Exception("Could not create program")

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, pixelShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val info = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw Exception("Could not link program: $info")
        }

        return program
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        val shader = GLES20.glCreateShader(shaderType)
        GlUtils.checkGlError("glCreateShader type=$shaderType")

        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val info = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw Exception("Could not compile shader $shaderType: $info")
        }

        return shader
    }

    private fun checkLocation(location: Int, label: String) {
        if (location < 0) {
            throw RuntimeException("Unable to locate '$label' in program")
        }
    }

    private fun getLogoResource(context: Context, resourceId: Int): Pair<Int, Float> {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        var ratio = 1f

        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options)
                ?: throw RuntimeException("Error loading bitmap: resource not found")

            ratio = bitmap.width.toFloat() / bitmap.height.toFloat()

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()
        }

        return Pair(textureHandle[0], ratio)
    }

    private fun createTextTexture(
        text: String,
        size: Float,
        textColor: Int,
        maxLengthText: String? = null,
        score: String? = null,
        turn: String? = null
    ): Pair<Int, Float> {
        // Create paints
        val paint = Paint().apply {
            textSize = size
            color = textColor
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        val scorePaint = Paint().apply {
            textSize = size
            color = Color.parseColor("#13235B")
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val turnPaint = Paint().apply {
            textSize = size
            color = textColor
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val backgroundPaint = Paint().apply {
            color = Color.parseColor("#13235B")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val scoreBackgroundPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }

        // Calculate dimensions
        val textBounds = Rect()
        val displayText = maxLengthText ?: text
        paint.getTextBounds(displayText, 0, displayText.length, textBounds)

        val scoreWidth = score?.let {
            val bounds = Rect()
            paint.getTextBounds("000", 0, 3, bounds)
            bounds.width()
        } ?: 0

        val turnWidth = turn?.let {
            val bounds = Rect()
            paint.getTextBounds("000", 0, 3, bounds)
            bounds.width()
        } ?: 0

        val padding = 14f
        val borderWidth = 2f
        val width = textBounds.width() + (padding * 2) + (borderWidth * 2) + scoreWidth + turnWidth
        val height = textBounds.height() + (padding * 2) + (borderWidth * 2)

        // Create bitmap and draw
        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        val bgRect = RectF(borderWidth, borderWidth, width - borderWidth, height - borderWidth)
        canvas.drawRect(bgRect, backgroundPaint)

        // Draw score background if needed
        score?.let {
            val scoreBgRect = RectF(
                borderWidth + textBounds.width() + turnWidth + (2 * padding),
                borderWidth,
                width - borderWidth,
                height - borderWidth
            )
            canvas.drawRect(scoreBgRect, scoreBackgroundPaint)
        }

        // Draw border
        val borderRect = RectF(borderWidth / 2, borderWidth / 2, width - borderWidth / 2, height - borderWidth / 2)
        canvas.drawRect(borderRect, borderPaint)

        // Draw text
        val textY = height - padding - textBounds.bottom - borderWidth
        canvas.drawText(text, padding + borderWidth, textY, paint)

        score?.let {
            canvas.drawText(it, borderWidth + textBounds.width() + turnWidth + (2 * padding) + (scoreWidth / 2), textY, scorePaint)
        }

        turn?.let {
            canvas.drawText(it, borderWidth + textBounds.width() + (2 * padding) + (turnWidth / 2), textY, turnPaint)
        }

        // Create OpenGL texture
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        return Pair(textureHandle[0], width / height)
    }

    private fun getMaxLengthText(): String? {
        return listOf(TEXT1, TEXT2).maxByOrNull { it.length }
    }

    private suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            inputStream = connection.inputStream
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            inputStream?.close()
            connection?.disconnect()
        }
    }

    private fun loadTextureFromBitmap(bitmap: Bitmap): Pair<Int, Float> {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            bitmap.recycle()
        }

        return Pair(textureHandle[0], aspectRatio)
    }

    // Shader generation methods
    private fun getVertexShader(type: String): String {
        val capitalizedType = type.capitalize()
        return """
            uniform mat4 u${capitalizedType}MVPMatrix;
            attribute vec2 a${capitalizedType}Position;
            attribute vec2 a${capitalizedType}TextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = u${capitalizedType}MVPMatrix * vec4(a${capitalizedType}Position, 0.0, 1.0);
                vTextureCoord = a${capitalizedType}TextureCoord;
            }
        """.trimIndent()
    }

    private fun getFragmentShader(type: String): String {
        return """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """.trimIndent()
    }

    companion object {
        // Public variables for external configuration
        var TEXT1 = ""
        var TEXT2 = ""
        var TEXT3 = ""
        var TEXT4 = ""
        var SCORE1 = ""
        var SCORE2 = ""
        var TURN1 = ""
        var TURN2 = ""
        var LINK1 = ""
        var LINK2 = ""
        var LINK3 = ""

        // Shader constants
        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER_EXT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }
}
