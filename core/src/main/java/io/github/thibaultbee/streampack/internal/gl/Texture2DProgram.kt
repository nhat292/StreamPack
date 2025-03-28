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
import android.graphics.Typeface
import android.opengl.Matrix
import io.github.thibaultbee.streampack.R

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

    private var logoTextureId: Int = -1

    private var textTextureId: Int = -1
    private var textWidth: Int = 0

    private var text2TextureId: Int = -1
    private var text2Width: Int = 0

    private var text3TextureId: Int = -1
    private var text3Width: Int = 0

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

        if (logoTextureId == -1) {
            logoTextureId = getLogoResource(context, R.drawable.logo)
            GlUtils.checkGlError("loadLogoTexture")
        }

        GLES20.glUseProgram(logoProgramHandle)
        GlUtils.checkGlError("glUseProgram logo")

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GlUtils.checkGlError("glActiveTexture")
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, logoTextureId)
        GlUtils.checkGlError("glBindTexture logo")

        // Set Texture Uniform for Logo
        val uLogoTextureLoc = GLES20.glGetUniformLocation(logoProgramHandle, "sTexture")
        GlUtils.checkGlError("glGetUniformLocation sTexture")
        if (uLogoTextureLoc != -1) {
            GLES20.glUniform1i(uLogoTextureLoc, 1)
            GlUtils.checkGlError("glUniform1i")
        }

        // Adjust Logo Position
        val logoMvpMatrix = FloatArray(16)
        Matrix.setIdentityM(logoMvpMatrix, 0)
        Matrix.translateM(logoMvpMatrix, 0, 0.85f, 0.85f, 0f) // Adjust position (top-right corner)
        Matrix.scaleM(logoMvpMatrix, 0, 0.117f, 0.2f, 1f)  // Scale down logo

        GLES20.glUniformMatrix4fv(uLogoMVPMatrixLoc, 1, false, logoMvpMatrix, 0)
        GlUtils.checkGlError("glUniformMatrix4fv logo")

        GLES20.glEnableVertexAttribArray(aLogoPositionLoc)
        GlUtils.checkGlError("glEnableVertexAttribArray logo position")
        GLES20.glVertexAttribPointer(aLogoPositionLoc, 2, GLES20.GL_FLOAT, false, 0, logoVertexBuffer)
        GlUtils.checkGlError("glVertexAttribPointer logo position")

        GLES20.glEnableVertexAttribArray(aLogoTextureCoordLoc)
        GlUtils.checkGlError("glEnableVertexAttribArray logo texture")
        GLES20.glVertexAttribPointer(aLogoTextureCoordLoc, 2, GLES20.GL_FLOAT, false, 0, logoTexBuffer)
        GlUtils.checkGlError("glVertexAttribPointer logo texture")

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GlUtils.checkGlError("glDrawArrays logo")

        // Create text

        if (TEXT1.isNotEmpty()) {
            if (textTextureId == -1 || OLD_TEXT1 != TEXT1) {
                val (id, width) = createTextTexture(context, TEXT1, 5f, Color.WHITE)
                textTextureId = id
                textWidth = width
                OLD_TEXT1 = TEXT1
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
            val horizontalScale = 1f
            val verticalScale = 1f  // Keep vertical scale more consistent
            val textMvpMatrix = FloatArray(16)
            Matrix.setIdentityM(textMvpMatrix, 0)
            Matrix.translateM(textMvpMatrix, 0, 0f, 0.83f, 0f)  // Top-left corner
            Matrix.scaleM(textMvpMatrix, 0, horizontalScale, verticalScale, 1f)  // Scale to appropriate size

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
            if (text2TextureId == -1 || OLD_TEXT2 != TEXT2) {
                val (id, width)  = createTextTexture(context, TEXT2, 5f, Color.WHITE)
                text2TextureId = id
                text2Width = width
                OLD_TEXT2 = TEXT2
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
            val horizontalScale = 1f
            val verticalScale = 1f  // Keep vertical scale more consistent
            val textMvpMatrix = FloatArray(16)
            Matrix.setIdentityM(textMvpMatrix, 0)
            Matrix.translateM(textMvpMatrix, 0, 0f, 0.76f, 0f)  // Top-left corner
            Matrix.scaleM(textMvpMatrix, 0, horizontalScale, verticalScale, 1f)  // Scale to appropriate size

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
                val (id, width) = createTextTexture(context, TEXT3, 5f, Color.WHITE)
                text3TextureId = id
                text3Width = width
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
            val horizontalScale = 1f
            val verticalScale = 1f  // Keep vertical scale more consistent
            val textMvpMatrix = FloatArray(16)
            Matrix.setIdentityM(textMvpMatrix, 0)
            Matrix.translateM(textMvpMatrix, 0, 0f, 0.9f, 0f)  // Top-left corner
            Matrix.scaleM(textMvpMatrix, 0, horizontalScale, verticalScale, 1f)  // Scale to appropriate size

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

        GLES20.glDisable(GLES20.GL_BLEND)
        GlUtils.checkGlError("glDisable GL_BLEND")

        // Restore OpenGL State
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aLogoPositionLoc)
        GLES20.glDisableVertexAttribArray(aLogoTextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aTextPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextTextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aText2PositionLoc)
        GLES20.glDisableVertexAttribArray(aText2TextureCoordLoc)
        GLES20.glDisableVertexAttribArray(aText3PositionLoc)
        GLES20.glDisableVertexAttribArray(aText3TextureCoordLoc)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glUseProgram(0)
    }

    private fun getLogoResource(context: Context, resourceId: Int): Int {
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            val options = BitmapFactory.Options()
            options.inScaled = false  // No pre-scaling
            options.inPreferredConfig = Bitmap.Config.ARGB_8888

            val bitmap = BitmapFactory.decodeResource(context.resources, resourceId, options) ?: throw RuntimeException("Error loading bitmap: resource not found")

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            bitmap.recycle()
        }

        return textureHandle[0]
    }

    private fun createTextTexture(context: Context, text: String, size: Float, textColor: Int): Pair<Int, Int>  {
        // Create a bitmap with room for the text
        val paint = Paint().apply {
            textSize = size
            color = textColor
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        // Measure text dimensions
        val textBounds = Rect()
        paint.getTextBounds(text, 0, text.length, textBounds)
        val charWidthEstimate = paint.measureText(text)
        val width = charWidthEstimate.toInt() + 16  // Add padding
        val height = textBounds.height() + 16  // Add padding

        // Create a bitmap and draw text on it
        val viewport = IntArray(4)
        GLES20.glGetIntegerv(GLES20.GL_VIEWPORT, viewport, 0)
        val screenWidth = viewport[2]
        val bitmap = Bitmap.createBitmap(500, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawText(text, 0f, height - 8f - textBounds.bottom, paint)

        // Create an OpenGL texture
        val textureHandle = IntArray(1)
        GLES20.glGenTextures(1, textureHandle, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

        // Set texture parameters
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Upload bitmap to texture
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        // Clean up
        bitmap.recycle()

        return textureHandle[0] to width
    }

    companion object {

        var TEXT1 = ""
        var OLD_TEXT1 = ""
        var TEXT2 = ""
        var OLD_TEXT2 = ""
        var TEXT3 = ""
        var OLD_TEXT3 = ""

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
    }

}