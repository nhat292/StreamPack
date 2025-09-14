/*
 * Copyright (C) 2022 Thibault B.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.thibaultbee.streampack.internal.utils.av.video.avc

import io.github.thibaultbee.streampack.internal.utils.av.buffer.ByteBufferWriter
import io.github.thibaultbee.streampack.internal.utils.av.video.ChromaFormat
import io.github.thibaultbee.streampack.internal.utils.extensions.put
import io.github.thibaultbee.streampack.internal.utils.extensions.putShort
import io.github.thibaultbee.streampack.internal.utils.extensions.removeStartCode
import io.github.thibaultbee.streampack.internal.utils.extensions.shl
import io.github.thibaultbee.streampack.internal.utils.extensions.startCodeSize
import java.nio.ByteBuffer

data class AVCDecoderConfigurationRecord(
    private val configurationVersion: Int = 0x01,
    private val profileIdc: Byte,
    private val profileCompatibility: Byte,
    private val levelIdc: Byte,
    private val chromaFormat: ChromaFormat = ChromaFormat.YUV420, // Always YUV420 on Android camera
    private val sps: List<ByteBuffer>,
    private val pps: List<ByteBuffer>
): ByteBufferWriter() {
    private val safeSps: List<ByteBuffer> = sps.map { buf ->
        if (buf.remaining() >= 4 && (buf.get(0).toInt() == 0x00 && buf.get(1).toInt() == 0x00)) {
            buf.removeStartCode()
        } else {
            buf
        }
    }

    private val safePps: List<ByteBuffer> = pps.map { buf ->
        if (buf.remaining() >= 4 && (buf.get(0).toInt() == 0x00 && buf.get(1).toInt() == 0x00)) {
            buf.removeStartCode()
        } else {
            buf
        }
    }

    override val size: Int = getSize(safeSps, safePps)

    override fun write(output: ByteBuffer) {
        output.put(configurationVersion.toByte()) // configurationVersion
        output.put(profileIdc) // AVCProfileIndication
        output.put(profileCompatibility) // profile_compatibility
        output.put(levelIdc) // AVCLevelIndication

        // Force NALU length = 4 bytes (00 00 00 01)
        output.put(0xFF.toByte()) // 6 bits reserved + lengthSizeMinusOne=3 → 4-byte NALU length

        // SPS
        output.put(((0b111 shl 5) or (safeSps.size and 0x1F)).toByte())
        safeSps.forEach {
            output.putShort(it.remaining())
            output.put(it)
        }

        // PPS
        output.put(safePps.size.toByte())
        safePps.forEach {
            output.putShort(it.remaining())
            output.put(it)
        }

        // Extended profile support (high profiles)
        if (profileIdc == 100.toByte() || profileIdc == 110.toByte()
            || profileIdc == 122.toByte() || profileIdc == 144.toByte()
        ) {
            output.put(((0b111111 shl 2) or chromaFormat.value.toInt()).toByte())
            output.put(0xF8.toByte()) // bit_depth_luma_minus8 = 0
            output.put(0xF8.toByte()) // bit_depth_chroma_minus8 = 0
            output.put(0) // num of scaling matrices
        }
    }

    companion object {
        private const val AVC_DECODER_CONFIGURATION_RECORD_SIZE = 7

        fun fromParameterSets(
            sps: ByteBuffer,
            pps: ByteBuffer
        ) = fromParameterSets(listOf(sps), listOf(pps))

        fun fromParameterSets(
            sps: List<ByteBuffer>,
            pps: List<ByteBuffer>
        ): AVCDecoderConfigurationRecord {
            val spsNoStart = sps.map { it.removeStartCode() }
            val profileIdc = spsNoStart[0].get(1)
            val profileCompatibility = spsNoStart[0].get(2)
            val levelIdc = spsNoStart[0].get(3)
            return AVCDecoderConfigurationRecord(
                profileIdc = profileIdc,
                profileCompatibility = profileCompatibility,
                levelIdc = levelIdc,
                sps = sps,
                pps = pps
            )
        }

        fun getSize(sps: List<ByteBuffer>, pps: List<ByteBuffer>): Int {
            var size = AVC_DECODER_CONFIGURATION_RECORD_SIZE
            sps.forEach { size += 2 + (it.remaining() - it.startCodeSize) }
            pps.forEach { size += 2 + (it.remaining() - it.startCodeSize) }
            val profileIdc = sps[0].get(sps[0].startCodeSize + 1).toInt()
            if (profileIdc in listOf(100, 110, 122, 144)) size += 4
            return size
        }
    }
}