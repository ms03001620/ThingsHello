package org.mark.carpi.protocol

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Wire contract from lib_unit_socket/ConnectedThread
 * 帧格式：4 字节小端 body 长度 + 1 字节 type + UTF-8 body。
 */
object FrameCodec {
    const val MAX_BODY = 500 * 1024

    data class Frame(
        val type: Int,
        val body: String,
    )

    /** 读一帧；在帧边界上干净 EOF 返回 null，截断或长度非法抛异常。 */
    @JvmStatic
    @Throws(IOException::class)
    fun read(input: InputStream): Frame? {
        val first = input.read()
        if (first < 0) return null
        val tail = input.readNBytes(4)
        if (tail.size != 4) throw EOFException("truncated header")
        val length = first or
            ((tail[0].toInt() and 0xFF) shl 8) or
            ((tail[1].toInt() and 0xFF) shl 16) or
            ((tail[2].toInt() and 0xFF) shl 24)
        if (length !in 1..MAX_BODY) throw IOException("invalid body length: ${Integer.toUnsignedString(length)}")
        val body = input.readNBytes(length)
        if (body.size != length) throw EOFException("truncated body")
        // type 按有符号字节解释，与旧 Java 实现一致（-1 编码后读回仍是 -1）。
        return Frame(tail[3].toInt(), String(body, Charsets.UTF_8))
    }

    @JvmStatic
    fun encode(type: Int, body: String): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..MAX_BODY) { "body size" }
        return ByteBuffer.allocate(5 + bytes.size).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(bytes.size).put(type.toByte()).put(bytes).array()
    }
}
