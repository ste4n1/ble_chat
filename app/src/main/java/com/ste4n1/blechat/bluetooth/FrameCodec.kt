package com.ste4n1.blechat.bluetooth

import java.io.InputStream
import java.io.OutputStream

data class Frame(val type: Int, val payload: ByteArray)

object FrameCodec {

    fun write(out: OutputStream, type: Int, payload: ByteArray) {
        val header = ByteArray(5)
        header[0] = type.toByte()
        header[1] = (payload.size ushr 24).toByte()
        header[2] = (payload.size ushr 16).toByte()
        header[3] = (payload.size ushr 8).toByte()
        header[4] = payload.size.toByte()
        synchronized(out) {
            out.write(header)
            if (payload.isNotEmpty()) out.write(payload)
            out.flush()
        }
    }

    fun read(input: InputStream): Frame? {
        val header = ByteArray(5)
        if (!readFully(input, header)) return null
        val type = header[0].toInt() and 0xFF
        val length = ((header[1].toInt() and 0xFF) shl 24) or
            ((header[2].toInt() and 0xFF) shl 16) or
            ((header[3].toInt() and 0xFF) shl 8) or
            (header[4].toInt() and 0xFF)
        if (length < 0 || length > BtConstants.MAX_FRAME_SIZE) return null
        val payload = ByteArray(length)
        if (length > 0 && !readFully(input, payload)) return null
        return Frame(type, payload)
    }

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = input.read(target, offset, target.size - offset)
            if (read <= 0) return false
            offset += read
        }
        return true
    }
}
