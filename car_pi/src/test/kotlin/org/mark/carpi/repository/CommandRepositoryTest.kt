package org.mark.carpi.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mark.carpi.model.Command
import org.mark.carpi.model.ControlEvent
import org.mark.carpi.repository.datasource.ControlConnection
import org.mark.carpi.repository.datasource.ControlDataSource
import org.mark.carpi.repository.protocol.FrameCodec
import java.io.ByteArrayInputStream
import java.io.InputStream

/** 用假数据源直接喂字节，不开端口。 */
class CommandRepositoryTest {
    private class FakeConnection(bytes: ByteArray) : ControlConnection {
        override val input: InputStream = ByteArrayInputStream(bytes)
        var closed = false
        override fun close() { closed = true }
    }

    private class FakeSource(vararg val conns: FakeConnection) : ControlDataSource {
        override fun connections(): Flow<ControlConnection> = conns.asFlow()
        override fun close() {}
    }

    private fun frames(vararg f: Pair<Int, String>) = f.fold(ByteArray(0)) { acc, (t, b) -> acc + FrameCodec.encode(t, b) }

    private fun describe(e: ControlEvent) = when (e) {
        ControlEvent.Connected -> "connected"
        ControlEvent.Disconnected -> "disconnected"
        is ControlEvent.Received -> "${e.command.type}:${e.command.command::class.simpleName}"
    }

    @Test
    fun parsesFramesInOrderBetweenConnectAndDisconnect() = runBlocking {
        val conn = FakeConnection(frames(0 to """{"left":35,"right":-20}""", 2 to """{"di":true}""", 3 to """{"x":1}"""))
        val events = CommandRepository(FakeSource(conn)).events().toList()
        assertEquals(listOf("connected", "0:Wheel", "2:Beep", "3:Unsupported", "disconnected"), events.map(::describe))
        assertTrue(conn.closed)
    }

    @Test
    fun badJsonIsInvalidCommandAndConnectionStays() = runBlocking {
        val conn = FakeConnection(frames(0 to "not json", 2 to """{"di":true}"""))
        val events = CommandRepository(FakeSource(conn)).events().toList()
        assertEquals(listOf("connected", "0:Invalid", "2:Beep", "disconnected"), events.map(::describe))
        assertTrue((events[1] as ControlEvent.Received).command.command is Command.Invalid)
    }

    @Test
    fun truncatedFrameEndsConnection() = runBlocking {
        val bytes = frames(2 to """{"di":true}""") + FrameCodec.encode(0, """{"left":1,"right":1}""").copyOf(7)
        val conn = FakeConnection(bytes)
        val events = CommandRepository(FakeSource(conn)).events().toList()
        assertEquals(listOf("connected", "2:Beep", "disconnected"), events.map(::describe))
        assertTrue(conn.closed)
    }

    @Test
    fun connectionsAreHandledOneAfterAnother() = runBlocking {
        val a = FakeConnection(frames(2 to """{"di":true}"""))
        val b = FakeConnection(frames(2 to """{"di":false}"""))
        val events = CommandRepository(FakeSource(a, b)).events().toList()
        assertEquals(listOf("connected", "2:Beep", "disconnected", "connected", "2:Beep", "disconnected"), events.map(::describe))
    }
}
