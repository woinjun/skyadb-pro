package com.sky22333.skyadb.pointer

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class MouseInputTest {
    @Test fun createPacketMatchesScrcpyFraming() {
        val data = ByteBuffer.wrap(MouseProtocol.create())
        assertEquals(12, data.get().toInt())
        assertEquals(1, data.short.toInt())
        assertEquals(0, data.short.toInt())
        assertEquals(0, data.short.toInt())
        val name = ByteArray(data.get().toInt()).also { data.get(it) }
        assertEquals("SkyADB Pro Mouse", name.toString(Charsets.UTF_8))
        val descriptor = ByteArray(data.short.toInt()).also { data.get(it) }
        assertArrayEquals(MouseProtocol.descriptor, descriptor)
        assertFalse(data.hasRemaining())
    }

    @Test fun signedMotionAndWheelRetainTheirValues() {
        val data = ByteBuffer.wrap(MouseProtocol.input(3, -127, 127, -2))
        assertEquals(13, data.get().toInt())
        assertEquals(1, data.short.toInt())
        assertEquals(4, data.short.toInt())
        assertEquals(3, data.get().toInt())
        assertEquals(-127, data.get().toInt())
        assertEquals(127, data.get().toInt())
        assertEquals(-2, data.get().toInt())
        assertFalse(data.hasRemaining())
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeMotionCannotWrapAround() { MouseProtocol.input(0, 128) }

    @Test fun accumulatedMotionIsSplitWithoutLosingDistance() {
        val buffer = MouseMotionBuffer()
        buffer.move(400f, -300f, -3f)
        val packets = buffer.drain()
        assertEquals(400, packets.sumOf { it[6].toInt() })
        assertEquals(-300, packets.sumOf { it[7].toInt() })
        assertEquals(-3, packets.sumOf { it[8].toInt() })
        assertTrue(buffer.drain().isEmpty())
    }

    @Test fun clickReleaseAndDragStateSurviveCoalescing() {
        val buffer = MouseMotionBuffer()
        buffer.button(1)
        buffer.button(0)
        val click = buffer.drain()
        assertEquals(listOf(1, 0), click.map { it[5].toInt() })
        buffer.button(1)
        buffer.move(20f, 0f)
        assertTrue(buffer.drain().all { it[5].toInt() == 1 })
        buffer.button(0)
        assertEquals(0, buffer.drain().single()[5].toInt())
    }

    @Test fun fractionalMovementAccumulatesAndInvalidSamplesAreIgnored() {
        val buffer = MouseMotionBuffer()
        buffer.move(Float.NaN, 0f)
        repeat(3) { buffer.move(0.25f, 0f) }
        assertTrue(buffer.drain().isEmpty())
        buffer.move(0.25f, 0f)
        assertEquals(1, buffer.drain().single()[6].toInt())
    }

    private fun calibrated(): GyroMotion = GyroMotion().also {
        for (i in 0..80) it.sample(1_000_000_000L + i * 10_000_000L, floatArrayOf(0.02f, -0.03f, 0f), 0, 1f)
        assertTrue(it.calibrated)
    }

    @Test fun stationaryBiasDoesNotMoveCursor() {
        val gyro = calibrated()
        val delta = gyro.sample(1_810_000_000L, floatArrayOf(0.02f, -0.03f, 0f), 0, 1f)
        assertEquals(0f, delta.first, 0.001f)
        assertEquals(0f, delta.second, 0.001f)
    }

    @Test fun yawMovesHorizontallyAndPauseGapDoesNotJump() {
        val gyro = calibrated()
        val delta = gyro.sample(1_810_000_000L, floatArrayOf(0.02f, 0.97f, 0f), 0, 1f)
        assertTrue(delta.first < 0f)
        assertEquals(0f, delta.second, 0.001f)
        assertEquals(0f to 0f, gyro.sample(5_000_000_000L, floatArrayOf(0.02f, 0.97f, 0f), 0, 1f))
    }

    @Test fun landscapeRotatesSensorAxes() {
        val gyro = calibrated()
        val delta = gyro.sample(1_810_000_000L, floatArrayOf(1.02f, -0.03f, 0f), 1, 1f)
        assertTrue(delta.first < 0f)
        assertEquals(0f, delta.second, 0.001f)
    }
}
