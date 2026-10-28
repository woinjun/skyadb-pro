package com.sky22333.skyadb.pointer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** scrcpy 5.0.1 control protocol, standard relative HID mouse (three buttons + wheel). */
object MouseProtocol {
    const val DeviceId = 1
    val descriptor: ByteArray
        get() = intArrayOf(
            0x05, 0x01, 0x09, 0x02, 0xa1, 0x01, 0x09, 0x01, 0xa1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
            0x95, 0x03, 0x75, 0x01, 0x81, 0x02, 0x95, 0x01, 0x75, 0x05,
            0x81, 0x03, 0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, 0x81, 0x25, 0x7f, 0x75, 0x08, 0x95, 0x03, 0x81, 0x06,
            0xc0, 0xc0,
        ).map { it.toByte() }.toByteArray()

    fun create(): ByteArray {
        val name = "SkyADB Pro Mouse".toByteArray(Charsets.UTF_8)
        val desc = descriptor
        return ByteBuffer.allocate(10 + name.size + desc.size).order(ByteOrder.BIG_ENDIAN)
            .put(12).putShort(DeviceId.toShort()).putShort(0).putShort(0)
            .put(name.size.toByte()).put(name).putShort(desc.size.toShort()).put(desc).array()
    }

    fun input(buttons: Int, x: Int = 0, y: Int = 0, wheel: Int = 0): ByteArray {
        require(buttons in 0..7 && x in -127..127 && y in -127..127 && wheel in -127..127)
        return ByteBuffer.allocate(9).order(ByteOrder.BIG_ENDIAN)
            .put(13).putShort(DeviceId.toShort()).putShort(4)
            .put(buttons.toByte()).put(x.toByte()).put(y.toByte()).put(wheel.toByte()).array()
    }

    fun destroy(): ByteArray = byteArrayOf(14, 0, DeviceId.toByte())
}

/** Coalesce motion without dropping button releases or fractional movement. Bounded backlog. */
class MouseMotionBuffer {
    private var x = 0f
    private var y = 0f
    private var wheel = 0f
    private var buttons = 0
    private val transitions = ArrayDeque<Int>()

    @Synchronized fun move(dx: Float, dy: Float, scroll: Float = 0f) {
        if (!dx.isFinite() || !dy.isFinite() || !scroll.isFinite()) return
        x = (x + dx).coerceIn(-4096f, 4096f)
        y = (y + dy).coerceIn(-4096f, 4096f)
        wheel = (wheel + scroll).coerceIn(-127f, 127f)
    }

    @Synchronized fun button(mask: Int) {
        require(mask in 0..7)
        check(transitions.size < 32) { "Input queue full" }
        transitions.addLast(mask)
    }

    @Synchronized fun drain(): List<ByteArray> = buildList {
        while (transitions.isNotEmpty()) {
            buttons = transitions.removeFirst()
            add(MouseProtocol.input(buttons))
        }
        repeat(8) {
            val dx = x.toInt().coerceIn(-127, 127)
            val dy = y.toInt().coerceIn(-127, 127)
            val dw = wheel.toInt().coerceIn(-127, 127)
            if (dx != 0 || dy != 0 || dw != 0) {
                add(MouseProtocol.input(buttons, dx, dy, dw))
                x -= dx
                y -= dy
                wheel -= dw
            }
        }
    }
}

/** Integrates rad/s; first 0.8 seconds estimate stationary bias. Never integrates pause gaps. */
class GyroMotion {
    private var previous = 0L
    private var first = 0L
    private var samples = 0
    private val bias = FloatArray(3)
    private var filteredX = 0f
    private var filteredY = 0f
    var calibrated = false
        private set

    fun sample(time: Long, values: FloatArray, rotation: Int, sensitivity: Float): Pair<Float, Float> {
        if (values.size < 3 || values.any { !it.isFinite() }) return 0f to 0f
        if (first == 0L) first = time
        if (!calibrated) {
            for (i in 0..2) bias[i] += values[i]
            samples++
            previous = time
            if (time - first >= 800_000_000L) {
                for (i in 0..2) bias[i] /= samples
                calibrated = true
            }
            return 0f to 0f
        }
        val dt = (time - previous) / 1_000_000_000f
        previous = time
        if (dt <= 0f || dt > 0.1f) {
            filteredX = 0f
            filteredY = 0f
            return 0f to 0f
        }
        val pitch = values[0] - bias[0]
        val yaw = values[1] - bias[1]
        val (horizontal, vertical) = when (rotation) {
            1 -> -pitch to yaw
            2 -> yaw to pitch
            3 -> pitch to -yaw
            else -> -yaw to -pitch
        }
        val alpha = 1f - kotlin.math.exp(-dt * 20f)
        filteredX += alpha * (horizontal - filteredX)
        filteredY += alpha * (vertical - filteredY)
        fun delta(rate: Float) = if (abs(rate) < 0.015f) 0f else rate * dt * 900f * sensitivity
        return delta(filteredX) to delta(filteredY)
    }
}
