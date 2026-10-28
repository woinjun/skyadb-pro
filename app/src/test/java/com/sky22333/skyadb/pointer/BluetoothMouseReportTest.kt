package com.sky22333.skyadb.pointer

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class BluetoothMouseReportTest {
    @Test fun stripsEnvelopeAndPreservesSignedAxesAndButtons() {
        assertArrayEquals(byteArrayOf(3, -127, 127, -1), MouseProtocol.hidReport(MouseProtocol.input(3, -127, 127, -1)))
    }

    @Test fun bootMouseOmitsWheel() {
        assertArrayEquals(byteArrayOf(2, 10, -20), MouseProtocol.hidReport(MouseProtocol.input(2, 10, -20, 4), true))
    }

    @Test fun completeClickKeepsReleaseInBluetoothReports() {
        val queue = MouseMotionBuffer()
        queue.button(1)
        queue.button(0)
        val reports = queue.drain().map { MouseProtocol.hidReport(it) }
        assertArrayEquals(byteArrayOf(1, 0, 0, 0), reports[0])
        assertArrayEquals(ByteArray(4), reports[1])
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsNonInputFrame() {
        MouseProtocol.hidReport(MouseProtocol.create())
    }
}
