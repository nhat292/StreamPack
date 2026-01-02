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

import android.opengl.GLES11Ext
import android.opengl.GLES20
import java.nio.FloatBuffer
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLUtils
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.opengl.Matrix
import io.github.thibaultbee.streampack.R
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

/**
 * GL program and supporting functions for textured 2D shapes.
 *
 * (Contains mostly code borrowed from CameraX)
 *
 */
class Texture2DProgram {
    // Handles to the GL program and various components of it.
    private val programHandle: Int
    private val uMVPMatrixLoc: Int
    private val uTexMatrixLoc: Int
    private val aPositionLoc: Int
    private val aTextureCoordLoc: Int

    private val logoProgramHandle: Int
    private val uLogoMVPMatrixLoc: Int
    private val aLogoPositionLoc: Int
    private val aLogoTextureCoordLoc: Int

    private val textProgramHandle: Int
    private val uTextMVPMatrixLoc: Int
    private val aTextPositionLoc: Int
    private val aTextTextureCoordLoc: Int

    private val text2ProgramHandle: Int
    private val uText2MVPMatrixLoc: Int
    private val aText2PositionLoc: Int
    private val aText2TextureCoordLoc: Int

    private val text3ProgramHandle: Int
    private val uText3MVPMatrixLoc: Int
    private val aText3PositionLoc: Int
    private val aText3TextureCoordLoc: Int

    private val text4ProgramHandle: Int
    private val uText4MVPMatrixLoc: Int
    private val aText4PositionLoc: Int
    private val aText4TextureCoordLoc: Int

    private val link1ProgramHandle: Int
    private val uLink1MVPMatrixLoc: Int
    private val aLink1PositionLoc: Int
    private val aLink1TextureCoordLoc: Int

    private val link2ProgramHandle: Int
    private val uLink2MVPMatrixLoc: Int
    private val aLink2PositionLoc: Int
    private val aLink2TextureCoordLoc: Int

    private val link3ProgramHandle: Int
    private val uLink3MVPMatrixLoc: Int
    private val aLink3PositionLoc: Int
    private val aLink3TextureCoordLoc: Int

    private var logoTextureId: Int = -1
    private var logoRatio: Float = 0f

    private var textTextureId: Int = -1
    private var textRatio: Float = 0f

    private var text2TextureId: Int = -1
    private var text2Ratio: Float = 0f

    private var text3TextureId: Int = -1
    private var text3Ratio: Float = 0f

    private var text4TextureId: Int = -1
    private var text4Ratio: Float = 0f

    private var link1TextureId: Int = -1
    private var link1Ratio: Float = 0f
    private var loadingLink1Bitmap: Boolean = false
    private var bitmapLink1: Bitmap? = null

    private var link2TextureId: Int = -1
    private var link2Ratio: Float = 0f
    private var loadingLink2Bitmap: Boolean = false
    private var bitmapLink2: Bitmap? = null

    private var link3TextureId: Int = -1
    private var link3Ratio: Float = 0f
    private var loadingLink3Bitmap: Boolean = false
    private var bitmapLink3: Bitmap? = null

    private val textScale = 0.08f
    private val startX = -0.98f
    private val startY = 0.95f
    private val spacing = 0.075f
    private val fixedScale = 0.6f

    init {
        programHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT)
        if (programHandle == 0) {
            throw RuntimeException("Unable to create program")
        }
        logoProgramHandle = createProgram(VERTEX_SHADER_2D, FRAGMENT_SHADER_2D)
        if (logoProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        textProgramHandle = createProgram(VERTEX_SHADER_2D_TEXT, FRAGMENT_SHADER_2D_TEXT)
        if (textProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        text2ProgramHandle = createProgram(VERTEX_SHADER_2D_TEXT2, FRAGMENT_SHADER_2D_TEXT2)
        if (text2ProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        text3ProgramHandle = createProgram(VERTEX_SHADER_2D_TEXT3, FRAGMENT_SHADER_2D_TEXT3)
        if (text3ProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        text4ProgramHandle = createProgram(VERTEX_SHADER_2D_TEXT4, FRAGMENT_SHADER_2D_TEXT4)
        if (text4ProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        link1ProgramHandle = createProgram(VERTEX_SHADER_2D_LINK1, FRAGMENT_SHADER_2D_LINK1)
        if (link1ProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        link2ProgramHandle = createProgram(VERTEX_SHADER_2D_LINK2, FRAGMENT_SHADER_2D_LINK2)
        if (link2ProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        link3ProgramHandle = createProgram(VERTEX_SHADER_2D_LINK3, FRAGMENT_SHADER_2D_LINK3)
        if (link3ProgramHandle == 0) {
            throw RuntimeException("Unable to create program")
        }

        // get locations of attributes and uniforms
        aPositionLoc = GLES20.glGetAttribLocation(programHandle, "aPosition")
        checkLocation(aPositionLoc, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(programHandle, "aTextureCoord")
        checkLocation(aTextureCoordLoc, "aTextureCoord")
        uMVPMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uMVPMatrix")
        checkLocation(uMVPMatrixLoc, "uMVPMatrix")
        uTexMatrixLoc = GLES20.glGetUniformLocation(programHandle, "uTexMatrix")
        checkLocation(uTexMatrixLoc, "uTexMatrix")

        // get locations of attributes and uniforms
        aLogoPositionLoc = GLES20.glGetAttribLocation(logoProgramHandle, "aLogoPosition")
        checkLocation(aLogoPositionLoc, "aLogoPosition")
        aLogoTextureCoordLoc = GLES20.glGetAttribLocation(logoProgramHandle, "aLogoTextureCoord")
        checkLocation(aLogoTextureCoordLoc, "aLogoTextureCoord")
        uLogoMVPMatrixLoc = GLES20.glGetUniformLocation(logoProgramHandle, "uLogoMVPMatrix")
        checkLocation(uLogoMVPMatrixLoc, "uLogoMVPMatrix")

        // get locations of attributes and uniforms
        aTextPositionLoc = GLES20.glGetAttribLocation(textProgramHandle, "aTextPosition")
        checkLocation(aTextPositionLoc, "aTextPosition")
        aTextTextureCoordLoc = GLES20.glGetAttribLocation(textProgramHandle, "aTextTextureCoord")
        checkLocation(aTextTextureCoordLoc, "aTextTextureCoord")
        uTextMVPMatrixLoc = GLES20.glGetUniformLocation(textProgramHandle, "uTextMVPMatrix")
        checkLocation(uTextMVPMatrixLoc, "uTextMVPMatrix")

        // get locations of attributes and uniforms
        aText2PositionLoc = GLES20.glGetAttribLocation(text2ProgramHandle, "aText2Position")
        checkLocation(aText2PositionLoc, "aText2Position")
        aText2TextureCoordLoc = GLES20.glGetAttribLocation(text2ProgramHandle, "aText2TextureCoord")
        checkLocation(aText2TextureCoordLoc, "aText2TextureCoord")
        uText2MVPMatrixLoc = GLES20.glGetUniformLocation(text2ProgramHandle, "uText2MVPMatrix")
        checkLocation(uText2MVPMatrixLoc, "uText2MVPMatrix")

        // get locations of attributes and uniforms
        aText3PositionLoc = GLES20.glGetAttribLocation(text3ProgramHandle, "aText3Position")
        checkLocation(aText3PositionLoc, "aText3Position")
        aText3TextureCoordLoc = GLES20.glGetAttribLocation(text3ProgramHandle, "aText3TextureCoord")
        checkLocation(aText3TextureCoordLoc, "aText3TextureCoord")
        uText3MVPMatrixLoc = GLES20.glGetUniformLocation(text3ProgramHandle, "uText3MVPMatrix")
        checkLocation(uText3MVPMatrixLoc, "uText3MVPMatrix")

        // get locations of attributes and uniforms
        aText4PositionLoc = GLES20.glGetAttribLocation(text4ProgramHandle, "aText4Position")
        checkLocation(aText4PositionLoc, "aText4Position")
        aText4TextureCoordLoc = GLES20.glGetAttribLocation(text4ProgramHandle, "aText4TextureCoord")
        checkLocation(aText4TextureCoordLoc, "aText4TextureCoord")
        uText4MVPMatrixLoc = GLES20.glGetUniformLocation(text4ProgramHandle, "uText4MVPMatrix")
        checkLocation(uText4MVPMatrixLoc, "uText4MVPMatrix")

        aLink1PositionLoc = GLES20.glGetAttribLocation(link1ProgramHandle, "aLink1Position")
        checkLocation(aLink1PositionLoc, "aLink1Position")
        aLink1TextureCoordLoc = GLES20.glGetAttribLocation(link1ProgramHandle, "aLink1TextureCoord")
        checkLocation(aLink1TextureCoordLoc, "aLink1TextureCoord")
        uLink1MVPMatrixLoc = GLES20.glGetUniformLocation(link1ProgramHandle, "uLink1MVPMatrix")
        checkLocation(uLink1MVPMatrixLoc, "uLink1MVPMatrix")

        aLink2PositionLoc = GLES20.glGetAttribLocation(link2ProgramHandle, "aLink2Position")
        checkLocation(aLink2PositionLoc, "aLink2Position")
        aLink2TextureCoordLoc = GLES20.glGetAttribLocation(link2ProgramHandle, "aLink2TextureCoord")
        checkLocation(aLink2TextureCoordLoc, "aLink2TextureCoord")
        uLink2MVPMatrixLoc = GLES20.glGetUniformLocation(link2ProgramHandle, "uLink2MVPMatrix")
        checkLocation(uLink2MVPMatrixLoc, "uLink2MVPMatrix")

        aLink3PositionLoc = GLES20.glGetAttribLocation(link3ProgramHandle, "aLink3Position")
        checkLocation(aLink3PositionLoc, "aLin3Position")
        aLink3TextureCoordLoc = GLES20.glGetAttribLocation(link3ProgramHandle, "aLink3TextureCoord")
        checkLocation(aLink3TextureCoordLoc, "aLink3TextureCoord")
        uLink3MVPMatrixLoc = GLES20.glGetUniformLocation(link3ProgramHandle, "uLink3MVPMatrix")
        checkLocation(uLink3MVPMatrixLoc, "uLink3MVPMatrix")


    }

    /**
     * Releases the program.
     *
     *
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    fun release() {
        GLES20.glDeleteProgram(programHandle)
        GLES20.glDeleteProgram(logoProgramHandle)
        GLES20.glDeleteProgram(textProgramHandle)
        GLES20.glDeleteProgram(text2ProgramHandle)
        GLES20.glDeleteProgram(text3ProgramHandle)
        GLES20.glDeleteProgram(text4ProgramHandle)
        GLES20.glDeleteProgram(link1ProgramHandle)
        GLES20.glDeleteProgram(link2ProgramHandle)
        GLES20.glDeleteProgram(link3ProgramHandle)
    }

    /**
     * Creates a texture object suitable for use with this program.
     * <p>
     * On exit, the texture will be bound.
     */
    fun createTextureObject(): Int {
        val textureID = IntArray(1)
        GLES20.glGenTextures(1, textureID, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID[0])
        GlUtils.checkGlError("glBindTexture mTextureID")

        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GlUtils.checkGlError("glTexParameter")
        return textureID[0]
    }


    /**
     * Creates a new program from the supplied vertex and fragment shaders.
     *
     * @return A handle to the program, or 0 on failure.
     */
    private fun createProgram(vertexSource: String?, fragmentSource: String?): Int {
        val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) {
            return 0
        }
        val pixelShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (pixelShader == 0) {
            return 0
        }
        val program = GLES20.glCreateProgram()
        GlUtils.checkGlError("glCreateProgram")
        if (program == 0) {
            throw Exception("Could not create program")
        }
        GLES20.glAttachShader(program, vertexShader)
        GlUtils.checkGlError("glAttachShader")
        GLES20.glAttachShader(program, pixelShader)
        GlUtils.checkGlError("glAttachShader")
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


    /**
     * Compiles the provided shader source.
     *
     * @return A handle to the shader, or 0 on failure.
     */
    private fun loadShader(shaderType: Int, source: String?): Int {
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

    /**
     * Checks to see if the location we obtained is valid.  GLES returns -1 if a label
     * could not be found, but does not set the GL error.
     *
     *
     * Throws a RuntimeException if the location is invalid.
     */
    private fun checkLocation(location: Int, label: String) {
        if (location < 0) {
            throw java.lang.RuntimeException("Unable to locate '$label' in program")
        }
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     * vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     * for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    @OptIn(DelicateCoroutinesApi::class)
    fun draw(
        context: Context,
        mvpMatrix: FloatArray, vertexBuffer: FloatBuffer, logoVertexBuffer: FloatBuffer, firstVertex: Int,
        vertexCount: Int, coordsPerVertex: Int, vertexStride: Int,
        texMatrix: FloatArray, texBuffer: FloatBuffer, logoTexBuffer: FloatBuffer, textureId: Int, texStride: Int,
        textVertexBuffer: FloatBuffer, textTexBuffer: FloatBuffer,
        text2VertexBuffer: FloatBuffer, text2TexBuffer: FloatBuffer,
        text3VertexBuffer: FloatBuffer, text3TexBuffer: FloatBuffer,
    ) {
        GlUtils.checkGlError("draw start")

        // 1️⃣ Draw Video Frame
        GLES20.glUseProgram(programHandle)
        GlUtils.checkGlError("glUseProgram")
        // Set the texture for the main video frame
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GlUtils.checkGlError("glUniformMatrix4fv")
        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        GlUtils.checkGlError("glUniformMatrix4fv")

        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GlUtils.checkGlError("glEnableVertexAttribArray")
        GLES20.glVertexAttribPointer(aPositionLoc, coordsPerVertex, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer)
        GlUtils.checkGlError("glVertexAttribPointer")

        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GlUtils.checkGlError("glEnableVertexAttribArray")
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, texStride, texBuffer)
        GlUtils.checkGlError("glVertexAttribPointer")

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        GlUtils.checkGlError("glDrawArrays")

        // 2️⃣ Draw Logo Overlay
        GLES20.glEnable(GLES20.GL_BLEND)
        GlUtils.checkGlError("glEnable GL_BLEND")
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GlUtils.checkGlError("glBlendFunc")
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GlUtils.checkGlError("glDisable GL_DEPTH_TEST")

//        if (logoTextureId == -1) {
//            val (id, ratio) = getLogoResource(context, R.drawable.logo)
//            logoTextureId = id
//            logoRatio = ratio
//            GlUtils.checkGlError("loadLogoTexture")
//        }
//
//        GLES20.glUseProgram(logoProgramHandle)
//        GlUtils.checkGlError("glUseProgram logo")
//
//        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
//        GlUtils.checkGlError("glActiveTexture")
//        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, logoTextureId)
//        GlUtils.checkGlError("glBindTexture logo")
//
//        // Set Texture Uniform for Logo
//        val uLogoTextureLoc = GLES20.glGetUniformLocation(logoProgramHandle, "sTexture")
//        GlUtils.checkGlError("glGetUniformLocation sTexture")
//        if (uLogoTextureLoc != -1) {
//            GLES20.glUniform1i(uLogoTextureLoc, 1)
//            GlUtils.checkGlError("glUniform1i")
//        }
//
//        // Adjust Logo Position
//        val logoScale = 0.2f
//        var logoHorizontalScale = logoScale * logoRatio
//        logoHorizontalScale -= (logoHorizontalScale * 0.5f)
//        val logoMvpMatrix = FloatArray(16)
//        Matrix.setIdentityM(logoMvpMatrix, 0)
//        Matrix.translateM(logoMvpMatrix, 0, 0.85f, 0.85f, 0f) // Adjust position (top-right corner)
//        Matrix.scaleM(logoMvpMatrix, 0, logoHorizontalScale, logoScale, 1f)  // Scale down logo
//
//        GLES20.glUniformMatrix4fv(uLogoMVPMatrixLoc, 1, false, logoMvpMatrix, 0)
//        GlUtils.checkGlError("glUniformMatrix4fv logo")
//
//        GLES20.glEnableVertexAttribArray(aLogoPositionLoc)
//        GlUtils.checkGlError("glEnableVertexAttribArray logo position")
//        GLES20.glVertexAttribPointer(aLogoPositionLoc, 2, GLES20.GL_FLOAT, false, 0, logoVertexBuffer)
//        GlUtils.checkGlError("glVertexAttribPointer logo position")
//
//        GLES20.glEnableVertexAttribArray(aLogoTextureCoordLoc)
//        GlUtils.checkGlError("glEnableVertexAttribArray logo texture")
//        GLES20.glVertexAttribPointer(aLogoTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, logoTexBuffer)
//        GlUtils.checkGlError("glVertexAttribPointer logo texture")
//
//        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
//        GlUtils.checkGlError("glDrawArrays logo")

        // Link 1 PNG
        if (LINK1.isNotEmpty() && !loadingLink1Bitmap) {
            if (bitmapLink1 == null || LINK1 != OLD_LINK1) {
                GlobalScope.launch(Dispatchers.IO) {
                    link1TextureId = -1
                    loadingLink1Bitmap = true
                    bitmapLink1 = loadBitmapFromUrl(LINK1)
                    loadingLink1Bitmap = false
                    OLD_LINK1 = LINK1
                }
            }
            if (bitmapLink1 != null && link1TextureId == -1) {
                val (id, ratio) = loadTextureFromBitmap(bitmapLink1!!)
                link1TextureId = id
                link1Ratio = ratio
            }

            if (link1TextureId != -1) {
                GLES20.glUseProgram(link1ProgramHandle)
                GlUtils.checkGlError("glUseProgram link1")

                GLES20.glActiveTexture(GLES20.GL_TEXTURE6)
                GlUtils.checkGlError("glActiveTexture")
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, link1TextureId)
                GlUtils.checkGlError("glBindTexture link1")

                // Set Texture Uniform for Logo
                val uLink1TextureLoc = GLES20.glGetUniformLocation(link1ProgramHandle, "sTexture")
                GlUtils.checkGlError("glGetUniformLocation sTexture")
                if (uLink1TextureLoc != -1) {
                    GLES20.glUniform1i(uLink1TextureLoc, 6)
                    GlUtils.checkGlError("glUniform1i")
                }

                // Adjust Logo Position
                val scale = 0.15f
                val horizontalScale = (scale * link1Ratio) * fixedScale
                val link1MvpMatrix = FloatArray(16)
                Matrix.setIdentityM(link1MvpMatrix, 0)
                Matrix.translateM(link1MvpMatrix, 0, 0.98f - (horizontalScale / 2), 0.85f, 0f) // Adjust position (top-right corner)
                Matrix.scaleM(link1MvpMatrix, 0, horizontalScale, scale, 1f)  // Scale down logo

                GLES20.glUniformMatrix4fv(uLink1MVPMatrixLoc, 1, false, link1MvpMatrix, 0)
                GlUtils.checkGlError("glUniformMatrix4fv link1")

                GLES20.glEnableVertexAttribArray(aLink1PositionLoc)
                GlUtils.checkGlError("glEnableVertexAttribArray link position")
                GLES20.glVertexAttribPointer(aLink1PositionLoc, 2, GLES20.GL_FLOAT, false, 0, logoVertexBuffer)
                GlUtils.checkGlError("glVertexAttribPointer logo position")

                GLES20.glEnableVertexAttribArray(aLink1TextureCoordLoc)
                GlUtils.checkGlError("glEnableVertexAttribArray link1 texture")
                GLES20.glVertexAttribPointer(aLink1TextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, logoTexBuffer)
                GlUtils.checkGlError("glVertexAttribPointer logo texture")

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtils.checkGlError("glDrawArrays logo")
            }
        }

        // Link 2 PNG
        if (LINK2.isNotEmpty() && !loadingLink2Bitmap) {
            if (bitmapLink2 == null || LINK2 != OLD_LINK2) {
                GlobalScope.launch(Dispatchers.IO) {
                    link2TextureId = -1
                    loadingLink2Bitmap = true
                    bitmapLink2 = loadBitmapFromUrl(LINK2)
                    loadingLink2Bitmap = false
                    OLD_LINK2 = LINK2
                }
            }
            if (bitmapLink2 != null && link2TextureId == -1) {
                val (id, ratio) = loadTextureFromBitmap(bitmapLink2!!)
                link2TextureId = id
                link2Ratio = ratio
            }

            if (link2TextureId != -1) {
                GLES20.glUseProgram(link2ProgramHandle)
                GlUtils.checkGlError("glUseProgram link2")

                GLES20.glActiveTexture(GLES20.GL_TEXTURE7)
                GlUtils.checkGlError("glActiveTexture")
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, link2TextureId)
                GlUtils.checkGlError("glBindTexture link2")

                // Set Texture Uniform for Logo
                val uLink1TextureLoc = GLES20.glGetUniformLocation(link2ProgramHandle, "sTexture")
                GlUtils.checkGlError("glGetUniformLocation sTexture")
                if (uLink1TextureLoc != -1) {
                    GLES20.glUniform1i(uLink1TextureLoc, 7)
                    GlUtils.checkGlError("glUniform1i")
                }

                // Adjust Logo Position
                val scale = 0.15f
                val horizontalScale = (scale * link2Ratio) * fixedScale
                val link2MvpMatrix = FloatArray(16)
                Matrix.setIdentityM(link2MvpMatrix, 0)
                Matrix.translateM(link2MvpMatrix, 0, startX + (horizontalScale / 2), -0.85f, 0f) // Adjust position (top-right corner)
                Matrix.scaleM(link2MvpMatrix, 0, horizontalScale, scale, 1f)  // Scale down logo

                GLES20.glUniformMatrix4fv(uLink2MVPMatrixLoc, 1, false, link2MvpMatrix, 0)
                GlUtils.checkGlError("glUniformMatrix4fv link2")

                GLES20.glEnableVertexAttribArray(aLink2PositionLoc)
                GlUtils.checkGlError("glEnableVertexAttribArray link position")
                GLES20.glVertexAttribPointer(aLink2PositionLoc, 2, GLES20.GL_FLOAT, false, 0, logoVertexBuffer)
                GlUtils.checkGlError("glVertexAttribPointer logo position")

                GLES20.glEnableVertexAttribArray(aLink2TextureCoordLoc)
                GlUtils.checkGlError("glEnableVertexAttribArray link2 texture")
                GLES20.glVertexAttribPointer(aLink2TextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, logoTexBuffer)
                GlUtils.checkGlError("glVertexAttribPointer logo texture")

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtils.checkGlError("glDrawArrays logo")
            }
        }

        // Link 3 PNG
        if (LINK3.isNotEmpty() && !loadingLink3Bitmap) {
            if (bitmapLink3 == null || LINK3 != OLD_LINK3) {
                GlobalScope.launch(Dispatchers.IO) {
                    link3TextureId = -1
                    loadingLink3Bitmap = true
                    bitmapLink3 = loadBitmapFromUrl(LINK3)
                    loadingLink3Bitmap = false
                    OLD_LINK3 = LINK3
                }
            }
            if (bitmapLink3 != null && link3TextureId == -1) {
                val (id, ratio) = loadTextureFromBitmap(bitmapLink3!!)
                link3TextureId = id
                link3Ratio = ratio
            }

            if (link3TextureId != -1) {
                GLES20.glUseProgram(link3ProgramHandle)
                GlUtils.checkGlError("glUseProgram link3")

                GLES20.glActiveTexture(GLES20.GL_TEXTURE8)
                GlUtils.checkGlError("glActiveTexture")
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, link3TextureId)
                GlUtils.checkGlError("glBindTexture link3")

                // Set Texture Uniform for Logo
                val uLink1TextureLoc = GLES20.glGetUniformLocation(link3ProgramHandle, "sTexture")
                GlUtils.checkGlError("glGetUniformLocation sTexture")
                if (uLink1TextureLoc != -1) {
                    GLES20.glUniform1i(uLink1TextureLoc, 8)
                    GlUtils.checkGlError("glUniform1i")
                }

                // Adjust Logo Position
                val scale = 0.15f
                val horizontalScale = (scale * link3Ratio) * fixedScale
                val link3MvpMatrix = FloatArray(16)
                Matrix.setIdentityM(link3MvpMatrix, 0)
                Matrix.translateM(link3MvpMatrix, 0, 0.98f - (horizontalScale / 2), -0.85f, 0f) // Adjust position (top-right corner)
                Matrix.scaleM(link3MvpMatrix, 0, horizontalScale, scale, 1f)  // Scale down logo

                GLES20.glUniformMatrix4fv(uLink3MVPMatrixLoc, 1, false, link3MvpMatrix, 0)
                GlUtils.checkGlError("glUniformMatrix4fv link3")

                GLES20.glEnableVertexAttribArray(aLink3PositionLoc)
                GlUtils.checkGlError("glEnableVertexAttribArray link position")
                GLES20.glVertexAttribPointer(aLink3PositionLoc, 2, GLES20.GL_FLOAT, false, 0, logoVertexBuffer)
                GlUtils.checkGlError("glVertexAttribPointer logo position")

                GLES20.glEnableVertexAttribArray(aLink3TextureCoordLoc)
                GlUtils.checkGlError("glEnableVertexAttribArray link3 texture")
                GLES20.glVertexAttribPointer(aLink3TextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, logoTexBuffer)
                GlUtils.checkGlError("glVertexAttribPointer logo texture")

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GlUtils.checkGlError("glDrawArrays logo")
            }
        }

        // Create text

        if (TEXT1.isNotEmpty()) {
            if (textTextureId == -1 || OLD_TEXT1 != TEXT1 || OLD_MATCH_SCORE1 != MATCH_SCORE1 || OLD_TURN1 != TURN1 || OLD_SCORE1 != SCORE1|| OLD_POINT1 != POINT1 || OLD_TB_SCORE1 != TB_SCORE1) {
                val (id, ratio) = createTextTexture(TEXT1, 30f, Color.WHITE, getMaxLengthText(), MATCH_SCORE1, SCORE1, POINT1, TURN1, TB_SCORE1)
                textTextureId = id
                textRatio = ratio
                OLD_TEXT1 = TEXT1
                OLD_MATCH_SCORE1 = MATCH_SCORE1
                OLD_SCORE1 = SCORE1
                OLD_POINT1 = POINT1
                OLD_TURN1 = TURN1
                OLD_TB_SCORE1 = TB_SCORE1
            }
            GLES20.glUseProgram(textProgramHandle)
            GlUtils.checkGlError("glUseProgram text")

            // Set up text rendering similar to logo rendering
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GlUtils.checkGlError("glActiveTexture")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textTextureId)
            GlUtils.checkGlError("glBindTexture text")

            val uTextTextureLoc = GLES20.glGetUniformLocation(textProgramHandle, "sTexture")
            if (uTextTextureLoc != -1) {
                GLES20.glUniform1i(uTextTextureLoc, 2)
                GlUtils.checkGlError("glUniform1i")
            }
            val horizontalScale = (textScale * textRatio) * fixedScale
            val textMvpMatrix = FloatArray(16)
            Matrix.setIdentityM(textMvpMatrix, 0)
            Matrix.translateM(textMvpMatrix, 0, startX + (horizontalScale / 2), startY - spacing, 0f)  // Top-left corner
            Matrix.scaleM(textMvpMatrix, 0, horizontalScale, textScale, 1f)  // Scale to appropriate size

            GLES20.glUniformMatrix4fv(uTextMVPMatrixLoc, 1, false, textMvpMatrix, 0)

            GLES20.glEnableVertexAttribArray(aTextPositionLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text position")
            GLES20.glVertexAttribPointer(aTextPositionLoc, 2, GLES20.GL_FLOAT, false, 0, textVertexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text position")

            GLES20.glEnableVertexAttribArray(aTextTextureCoordLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text texture")
            GLES20.glVertexAttribPointer(aTextTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, textTexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text texture")

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtils.checkGlError("glDrawArrays text")
        }

        // Create text
        if (TEXT2.isNotEmpty()) {
            if (text2TextureId == -1 || OLD_TEXT2 != TEXT2 || OLD_MATCH_SCORE2 != MATCH_SCORE2 || OLD_TURN2 != TURN2 || OLD_SCORE2 != SCORE2 || OLD_POINT2 != POINT2 || OLD_TB_SCORE2 != TB_SCORE2) {
                val (id, ratio)  = createTextTexture(TEXT2, 30f, Color.WHITE, getMaxLengthText(), MATCH_SCORE2, SCORE2, POINT2, TURN2, TB_SCORE2)
                text2TextureId = id
                text2Ratio = ratio
                OLD_TEXT2 = TEXT2
                OLD_MATCH_SCORE2 = MATCH_SCORE2
                OLD_SCORE2 = SCORE2
                OLD_POINT2 = POINT2
                OLD_TURN2 = TURN2
                OLD_TB_SCORE2 = TB_SCORE2
            }
            GLES20.glUseProgram(text2ProgramHandle)
            GlUtils.checkGlError("glUseProgram text")

            // Set up text rendering similar to logo rendering
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
            GlUtils.checkGlError("glActiveTexture")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, text2TextureId)
            GlUtils.checkGlError("glBindTexture text")

            val uTextTextureLoc = GLES20.glGetUniformLocation(text2ProgramHandle, "sTexture")
            if (uTextTextureLoc != -1) {
                GLES20.glUniform1i(uTextTextureLoc, 3)
                GlUtils.checkGlError("glUniform1i")
            }
            val horizontalScale = (textScale * text2Ratio) * fixedScale
            val textMvpMatrix = FloatArray(16)
            Matrix.setIdentityM(textMvpMatrix, 0)
            Matrix.translateM(textMvpMatrix, 0, startX + (horizontalScale / 2), startY - (2 * spacing), 0f)  // Top-left corner
            Matrix.scaleM(textMvpMatrix, 0, horizontalScale, textScale, 1f)  // Scale to appropriate size

            GLES20.glUniformMatrix4fv(uText2MVPMatrixLoc, 1, false, textMvpMatrix, 0)

            GLES20.glEnableVertexAttribArray(aText2PositionLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text position")
            GLES20.glVertexAttribPointer(aText2PositionLoc, 2, GLES20.GL_FLOAT, false, 0, text2VertexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text position")

            GLES20.glEnableVertexAttribArray(aText2TextureCoordLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text texture")
            GLES20.glVertexAttribPointer(aText2TextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, text2TexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text texture")

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtils.checkGlError("glDrawArrays text")
        }

        // Create text
        if (TEXT3.isNotEmpty()) {
            if (text3TextureId == -1 || OLD_TEXT3 != TEXT3) {
                val (id, ratio) = createTextTexture(TEXT3, 30f, Color.WHITE)
                text3TextureId = id
                text3Ratio = ratio
                OLD_TEXT3 = TEXT3
            }
            GLES20.glUseProgram(text3ProgramHandle)
            GlUtils.checkGlError("glUseProgram text")

            // Set up text rendering similar to logo rendering
            GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
            GlUtils.checkGlError("glActiveTexture")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, text3TextureId)
            GlUtils.checkGlError("glBindTexture text")

            val uTextTextureLoc = GLES20.glGetUniformLocation(text3ProgramHandle, "sTexture")
            if (uTextTextureLoc != -1) {
                GLES20.glUniform1i(uTextTextureLoc, 4)
                GlUtils.checkGlError("glUniform1i")
            }
            val horizontalScale = (textScale * text3Ratio) * fixedScale
            val textMvpMatrix = FloatArray(16)
            Matrix.setIdentityM(textMvpMatrix, 0)
            Matrix.translateM(textMvpMatrix, 0, startX + (horizontalScale / 2), startY, 0f)  // Top-left corner
            Matrix.scaleM(textMvpMatrix, 0, horizontalScale, textScale, 1f)  // Scale to appropriate size

            GLES20.glUniformMatrix4fv(uText3MVPMatrixLoc, 1, false, textMvpMatrix, 0)

            GLES20.glEnableVertexAttribArray(aText3PositionLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text position")
            GLES20.glVertexAttribPointer(aText3PositionLoc, 2, GLES20.GL_FLOAT, false, 0, text3VertexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text position")

            GLES20.glEnableVertexAttribArray(aText3TextureCoordLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text texture")
            GLES20.glVertexAttribPointer(aText3TextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, text3TexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text texture")

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtils.checkGlError("glDrawArrays text")
        }

        // Create text
        if (TEXT4.isNotEmpty()) {
            if (text4TextureId == -1 || OLD_TEXT4 != TEXT4) {
                val (id, ratio) = createTextTexture(TEXT4, 30f, Color.WHITE)
                text4TextureId = id
                text4Ratio = ratio
                OLD_TEXT4 = TEXT4
            }
            GLES20.glUseProgram(text4ProgramHandle)
            GlUtils.checkGlError("glUseProgram text")

            // Set up text rendering similar to logo rendering
            GLES20.glActiveTexture(GLES20.GL_TEXTURE5)
            GlUtils.checkGlError("glActiveTexture")
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, text4TextureId)
            GlUtils.checkGlError("glBindTexture text")

            val uTextTextureLoc = GLES20.glGetUniformLocation(text4ProgramHandle, "sTexture")
            if (uTextTextureLoc != -1) {
                GLES20.glUniform1i(uTextTextureLoc, 5)
                GlUtils.checkGlError("glUniform1i")
            }
            val horizontalScale = (textScale * text4Ratio) * fixedScale
            val textMvpMatrix = FloatArray(16)
            Matrix.setIdentityM(textMvpMatrix, 0)
            Matrix.translateM(textMvpMatrix, 0, startX + (horizontalScale / 2), startY - (3 * spacing), 0f)  // Top-left corner
            Matrix.scaleM(textMvpMatrix, 0, horizontalScale, textScale, 1f)  // Scale to appropriate size

            GLES20.glUniformMatrix4fv(uText4MVPMatrixLoc, 1, false, textMvpMatrix, 0)

            GLES20.glEnableVertexAttribArray(aText4PositionLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text position")
            GLES20.glVertexAttribPointer(aText4PositionLoc, 2, GLES20.GL_FLOAT, false, 0, text3VertexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text position")

            GLES20.glEnableVertexAttribArray(aText4TextureCoordLoc)
            GlUtils.checkGlError("glEnableVertexAttribArray text texture")
            GLES20.glVertexAttribPointer(aText4TextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, text3TexBuffer)
            GlUtils.checkGlError("glVertexAttribPointer text texture")

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtils.checkGlError("glDrawArrays text")
        }

        GLES20.glDisable(GLES20.GL_BLEND)
        GlUtils.checkGlError("glDisable GL_BLEND")

        // Restore OpenGL State
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aLogoPositionLoc)
        GLES20.glDisableVertexAttribArray(aLogoTextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aLink1PositionLoc)
        GLES20.glDisableVertexAttribArray(aLink1TextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aLink2PositionLoc)
        GLES20.glDisableVertexAttribArray(aLink2TextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aLink3PositionLoc)
        GLES20.glDisableVertexAttribArray(aLink3TextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aTextPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextTextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aText2PositionLoc)
        GLES20.glDisableVertexAttribArray(aText2TextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aText3PositionLoc)
        GLES20.glDisableVertexAttribArray(aText3TextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aText4PositionLoc)
        GLES20.glDisableVertexAttribArray(aText4TextureCoordLoc)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private fun getLogoResource(context: Context, resourceId: Int): Pair<Int, Float> {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        var ratio = 1f
        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options()
            options.inScaled = false  // No pre-scaling
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options) ?: throw RuntimeException("Error loading bitmap: resource not found")

            ratio = (bitmap.width / bitmap.height).toFloat()

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
        matchScore: String? = null,
        score: String? = null,
        point: String? = null,
        turn: String? = null,
        tieBreakScore: String? = null,
    ): Pair<Int, Float> {

        fun createTextPaint(color: Int, align: Paint.Align = Paint.Align.LEFT): Paint =
            Paint().apply {
                this.textSize = size
                this.color = color
                isAntiAlias = true
                typeface = Typeface.DEFAULT_BOLD
                textAlign = align
            }

        fun createBgPaint(color: Int): Paint =
            Paint().apply {
                this.color = color
                style = Paint.Style.FILL
                isAntiAlias = true
            }

        fun measureTextWidth(paint: Paint, sample: String): Int =
            Rect().apply { paint.getTextBounds(sample, 0, sample.length, this) }.width()

        val padding = 14f
        val borderWidth = 2f
        val contentText = maxLengthText ?: text

        val mainTextPaint = createTextPaint(textColor)
        val mainBgPaint = createBgPaint(Color.parseColor("#13235B"))
        val matchScorePaint = createTextPaint(Color.BLACK, Paint.Align.CENTER)
        val matchScoreBgPaint = createBgPaint(Color.WHITE)
        val scorePaint = createTextPaint(Color.WHITE, Paint.Align.CENTER)
        val scoreBgPaint = createBgPaint(Color.parseColor("#064C9B"))
        val pointPaint = createTextPaint(Color.RED, Paint.Align.CENTER)
        val pointBgPaint = createBgPaint(Color.YELLOW)
        val turnPaint = createTextPaint(textColor, Paint.Align.CENTER)
        val tieBreakPaint = createTextPaint(Color.WHITE, Paint.Align.CENTER)
        val tieBreakBgPaint = createBgPaint(Color.RED)

        val borderPaint = Paint().apply {
            color = Color.GRAY
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            isAntiAlias = true
        }

        val textBounds = Rect().apply {
            mainTextPaint.getTextBounds(contentText, 0, contentText.length, this)
        }
        val textHeight = textBounds.height()
        val textWidth = textBounds.width()

        val widths = mutableMapOf<String, Int>()

        matchScore?.let { widths["matchScore"] = measureTextWidth(mainTextPaint, "000") }
        score?.let { widths["score"] = measureTextWidth(mainTextPaint, "000") }
        point?.let { widths["point"] = measureTextWidth(mainTextPaint, "0000") }
        turn?.let { widths["turn"] = measureTextWidth(mainTextPaint, "000") }
        tieBreakScore?.let { widths["tieBreakScore"] = measureTextWidth(mainTextPaint, "000") }

        val totalExtraWidth = widths.values.sum()
        val width = textWidth + totalExtraWidth + (padding * 2) + (borderWidth * 2)
        val height = textHeight + (padding * 2) + (borderWidth * 2)

        val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background
        canvas.drawRect(
            RectF(borderWidth, borderWidth, width - borderWidth, height - borderWidth),
            mainBgPaint
        )

        var cursorX = padding + borderWidth
        val baselineY = height - padding - textBounds.bottom - borderWidth

        // Draw main text
        canvas.drawText(text, cursorX, baselineY, mainTextPaint)
        cursorX += textWidth

        fun drawBlock(value: String?, widthKey: String, bgPaint: Paint, textPaint: Paint, addPadding: Boolean = false) {
            if (value != null) {
                if (addPadding) {
                    cursorX += padding
                }
                val blockWidth = widths[widthKey]?.toFloat() ?: return
                val rect = RectF(cursorX, borderWidth, cursorX + blockWidth, height - borderWidth)
                canvas.drawRect(rect, bgPaint)

                if (widthKey == "turn" && IS_TENNIS && value.isNotEmpty()) {
                    // Draw a circle in the center of the block
                    val turnCirclePaint = Paint(turnPaint).apply {
                        style = Paint.Style.FILL
                        color = Color.GREEN
                    }
                    val cx = rect.centerX()
                    val cy = rect.centerY()
                    val radius = min(rect.width(), rect.height()) / 3.5f
                    canvas.drawCircle(cx, cy, radius, turnCirclePaint)
                } else {
                    // Draw text centered in the block
                    canvas.drawText(value, rect.centerX(), baselineY, textPaint)
                }

                cursorX += blockWidth
            }
        }

        drawBlock(turn, "turn", mainBgPaint, turnPaint, true)
        drawBlock(matchScore, "matchScore", matchScoreBgPaint, matchScorePaint)
        drawBlock(score, "score", scoreBgPaint, scorePaint)
        drawBlock(point, "point", pointBgPaint, pointPaint)
        drawBlock(tieBreakScore, "tieBreakScore", tieBreakBgPaint, tieBreakPaint)

        // Draw border
        canvas.drawRect(
            RectF(borderWidth / 2, borderWidth / 2, width - borderWidth / 2, height - borderWidth / 2),
            borderPaint
        )

        // Create texture
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
        val allTexts = listOf(TEXT1, TEXT2)
        return allTexts.maxByOrNull { it.length }
    }

    private fun loadBitmapFromUrl(url: String): Bitmap? {
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            inputStream = connection.inputStream
            return BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            inputStream?.close()
            connection?.disconnect()
        }
    }

    private fun loadTextureFromBitmap(bitmap: Bitmap): Pair<Int, Float> {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        // Calculate aspect ratio correctly as float
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

            // Load the bitmap into the bound texture
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

            // Set texture parameters
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            // Unbind the texture
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            // Recycle the bitmap to free memory
            bitmap.recycle()
        }

        return Pair(textureHandle[0], aspectRatio)
    }

    companion object {

        var TEXT1 = ""
        var OLD_TEXT1 = ""
        var TEXT2 = ""
        var OLD_TEXT2 = ""
        var TEXT3 = ""
        var OLD_TEXT3 = ""
        var TEXT4 = ""
        var OLD_TEXT4 = ""
        var SCORE1 = ""
        var OLD_SCORE1 = ""
        var SCORE2 = ""
        var OLD_SCORE2 = ""
        var TURN1 = ""
        var OLD_TURN1 = ""
        var TURN2 = ""
        var OLD_TURN2 = ""

        var LINK1 = ""
        var OLD_LINK1 = ""

        var LINK2 = ""
        var OLD_LINK2 = ""

        var LINK3 = ""
        var OLD_LINK3 = ""

        var MATCH_SCORE1 = ""
        var OLD_MATCH_SCORE1 = ""

        var MATCH_SCORE2 = ""
        var OLD_MATCH_SCORE2 = ""

        var POINT1: String? = null
        var OLD_POINT1: String? = null

        var POINT2: String? = null
        var OLD_POINT2: String? = null

        var TB_SCORE1: String? = null
        var OLD_TB_SCORE1: String? = null

        var TB_SCORE2: String? = null
        var OLD_TB_SCORE2: String? = null

        var IS_TENNIS = false

        // Simple vertex shader, used for all programs.
        private const val VERTEX_SHADER = """uniform mat4 uMVPMatrix;
    uniform mat4 uTexMatrix;
    attribute vec4 aPosition;
    attribute vec4 aTextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uMVPMatrix * aPosition;
        vTextureCoord = (uTexMatrix * aTextureCoord).xy;
    }
    """

        // Simple fragment shader for use with external 2D textures (e.g. what we get from
        // SurfaceTexture).
        private const val FRAGMENT_SHADER_EXT = """#extension GL_OES_EGL_image_external : require
    precision mediump float;
    varying vec2 vTextureCoord;
    uniform samplerExternalOES sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D = """uniform mat4 uLogoMVPMatrix;
    attribute vec2 aLogoPosition;
    attribute vec2 aLogoTextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uLogoMVPMatrix * vec4(aLogoPosition, 0.0, 1.0);
        vTextureCoord = aLogoTextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D_TEXT = """uniform mat4 uTextMVPMatrix;
    attribute vec2 aTextPosition;
    attribute vec2 aTextTextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uTextMVPMatrix * vec4(aTextPosition, 0.0, 1.0);
        vTextureCoord = aTextTextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D_TEXT = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D_TEXT2 = """uniform mat4 uText2MVPMatrix;
    attribute vec2 aText2Position;
    attribute vec2 aText2TextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uText2MVPMatrix * vec4(aText2Position, 0.0, 1.0);
        vTextureCoord = aText2TextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D_TEXT2 = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D_TEXT3 = """uniform mat4 uText3MVPMatrix;
    attribute vec2 aText3Position;
    attribute vec2 aText3TextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uText3MVPMatrix * vec4(aText3Position, 0.0, 1.0);
        vTextureCoord = aText3TextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D_TEXT3 = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D_TEXT4 = """uniform mat4 uText4MVPMatrix;
    attribute vec2 aText4Position;
    attribute vec2 aText4TextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uText4MVPMatrix * vec4(aText4Position, 0.0, 1.0);
        vTextureCoord = aText4TextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D_TEXT4 = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D_LINK1 = """uniform mat4 uLink1MVPMatrix;
    attribute vec2 aLink1Position;
    attribute vec2 aLink1TextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uLink1MVPMatrix * vec4(aLink1Position, 0.0, 1.0);
        vTextureCoord = aLink1TextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D_LINK1 = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D_LINK2 = """uniform mat4 uLink2MVPMatrix;
    attribute vec2 aLink2Position;
    attribute vec2 aLink2TextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uLink2MVPMatrix * vec4(aLink2Position, 0.0, 1.0);
        vTextureCoord = aLink2TextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D_LINK2 = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """

        private const val VERTEX_SHADER_2D_LINK3 = """uniform mat4 uLink3MVPMatrix;
    attribute vec2 aLink3Position;
    attribute vec2 aLink3TextureCoord;
    varying vec2 vTextureCoord;
    void main() {
        gl_Position = uLink3MVPMatrix * vec4(aLink3Position, 0.0, 1.0);
        vTextureCoord = aLink3TextureCoord;
    }
    """

        private const val FRAGMENT_SHADER_2D_LINK3 = """precision mediump float;
    varying vec2 vTextureCoord;
    uniform sampler2D sTexture;
    void main() {
        gl_FragColor = texture2D(sTexture, vTextureCoord);
    }
    """
    }

}