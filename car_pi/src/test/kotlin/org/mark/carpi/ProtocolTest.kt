package org.mark.carpi

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mark.carpi.protocol.FrameCodec
import org.mark.carpi.runtime.ProtocolScenario
import java.io.ByteArrayInputStream
import java.io.IOException

class ProtocolTest {
    @Test
    fun goldenHeaderAndUtf8() {
        val encoded = FrameCodec.encode(-1, "你好")
        assertArrayEquals(byteArrayOf(6, 0, 0, 0, -1), encoded.copyOf(5))
        val frame = FrameCodec.read(ByteArrayInputStream(encoded))!!
        assertEquals(-1, frame.type)
        assertEquals("你好", frame.body)
    }

    @Test
    fun rejectsPartialAndOversizedFrames() {
        val invalid = listOf(
            byteArrayOf(1), byteArrayOf(1, 0, 0, 0, 0), byteArrayOf(0, 0, 0, 0, 0),
            byteArrayOf(-1, -1, -1, -1, 0), byteArrayOf(1, 0, 8, 0, 0),
        )
        for (bytes in invalid) assertThrows(IOException::class.java) { FrameCodec.read(ByteArrayInputStream(bytes)) }
    }

    @Test
    fun cleanEof() = assertNull(FrameCodec.read(ByteArrayInputStream(ByteArray(0))))

    @Test
    fun realTcpAndRecordedBehavior() = ProtocolScenario.run()
}
