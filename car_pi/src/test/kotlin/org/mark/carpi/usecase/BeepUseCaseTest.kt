package org.mark.carpi.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mark.carpi.FakeScheduler
import org.mark.carpi.recordingRepository

class BeepUseCaseTest {
    @Test
    fun buzzerStopsAfter500ms() {
        val log = mutableListOf<String>()
        val repo = recordingRepository { command, action, value -> log += "$command $action $value" }
        val sch = FakeScheduler()
        val beep = BeepUseCase(repo, sch)
        beep(1); sch.advance(499)
        assertEquals(listOf("1 buzzer.enabled true"), log)
        sch.advance(1)
        assertEquals(listOf("1 buzzer.enabled true", "1 buzzer.enabled false"), log)
    }
}
