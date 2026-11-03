package org.mark.mobile.usecase

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mark.mobile.FakeUiScheduler
import org.mark.mobile.ctrl.Throttle
import org.mark.mobile.ctrl.WheelSpeed

class UseCasesAndThrottleTest {

    @Test
    fun `DriveJoystickUseCase mixSpeed matches four-quadrant differential wheel math`() {
        // 90° forward -> (100, 100)
        assertEquals(WheelSpeed(100, 100), DriveJoystickUseCase.mixSpeed(90, 100))
        // 270° backward -> (-100, -100)
        assertEquals(WheelSpeed(-100, -100), DriveJoystickUseCase.mixSpeed(270, 100))
        // 180° left -> (0, 100)
        assertEquals(WheelSpeed(0, 100), DriveJoystickUseCase.mixSpeed(180, 100))
        // 0° right -> (100, 0)
        assertEquals(WheelSpeed(100, 0), DriveJoystickUseCase.mixSpeed(0, 100))
        // Negative angle or zero max -> (0, 0)
        assertEquals(WheelSpeed(0, 0), DriveJoystickUseCase.mixSpeed(-1, 100))
        assertEquals(WheelSpeed(0, 0), DriveJoystickUseCase.mixSpeed(90, 0))
    }

    @Test
    fun `Throttle rate-limits bursts, flushes trailing value, deduplicates and supports sendNow`() {
        val scheduler = FakeUiScheduler()
        val emitted = mutableListOf<WheelSpeed>()
        val throttle = Throttle<WheelSpeed>(
            intervalMs = 50L,
            scheduler = scheduler,
            clock = { scheduler.nowMs }
        ) { emitted.add(it) }

        // First value emits immediately at t=0
        throttle.submit(WheelSpeed(20, 20))
        assertEquals(listOf(WheelSpeed(20, 20)), emitted)

        // Burst within 50ms -> coalesced, only trailing value flushed at t=50
        scheduler.advanceBy(10L)
        throttle.submit(WheelSpeed(30, 30))
        scheduler.advanceBy(10L)
        throttle.submit(WheelSpeed(50, 50))
        assertEquals(1, emitted.size)

        scheduler.advanceBy(30L) // t=50
        assertEquals(listOf(WheelSpeed(20, 20), WheelSpeed(50, 50)), emitted)

        // Identical consecutive value is deduplicated
        scheduler.advanceBy(100L)
        throttle.submit(WheelSpeed(50, 50))
        assertEquals(2, emitted.size)

        // sendNow drops pending value and emits immediately
        scheduler.advanceBy(10L)
        throttle.submit(WheelSpeed(60, 60)) // immediate at t=160
        scheduler.advanceBy(10L)
        throttle.submit(WheelSpeed(70, 70)) // pending
        throttle.sendNow(WheelSpeed(0, 0))  // immediate stop, drops (70, 70)
        scheduler.advanceBy(100L)
        assertEquals(
            listOf(
                WheelSpeed(20, 20),
                WheelSpeed(50, 50),
                WheelSpeed(60, 60),
                WheelSpeed(0, 0)
            ),
            emitted
        )

        // reset() invalidates deduplication cache so subsequent stop sendNow(0, 0) after external rotate is NOT dropped
        throttle.sendNow(WheelSpeed(0, 0))
        assertEquals(4, emitted.size) // still deduplicated when no external action occurred

        throttle.reset() // external rotate starts while lastSent was (0, 0)
        throttle.sendNow(WheelSpeed(0, 0)) // emergency stop on onStop() must NOT be dropped!
        assertEquals(5, emitted.size)
        assertEquals(WheelSpeed(0, 0), emitted.last())

        // reset() also cancels any pending delayed flush so stale joystick values never overwrite rotate
        scheduler.advanceBy(10L) // within 50ms window of previous emit
        throttle.submit(WheelSpeed(40, 40)) // pending
        throttle.reset()
        scheduler.advanceBy(100L)
        assertEquals(5, emitted.size)
    }

    @Test
    fun `SaveSnapshotUseCase handles null bitmap and permission check`() {
        var hasPerm = false
        val useCase = SaveSnapshotUseCase(object : ISnapshotStore {
            override fun hasWritePermission(): Boolean = hasPerm
            override fun saveToGallery(bitmap: android.graphics.Bitmap): android.net.Uri? = null
        })

        assertEquals(SnapshotResult.NO_FRAME, useCase(null))
    }
}
