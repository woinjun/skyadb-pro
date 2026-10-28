package com.sky22333.skyadb.pointer

import com.sky22333.skyadb.ui.pointer.PointerController
import org.junit.Assert.*
import org.junit.Test

class PointerControllerTest {
    private class Controller : PointerController() {
        override fun connect() { state.value = state.value.copy(ready = true) }
        override fun disconnect() { state.value = state.value.copy(ready = false) }
        override fun inputFailed(error: Exception) { throw error }
        fun reports() = motion.drain().map { MouseProtocol.hidReport(it) }
    }

    @Test fun disconnectedInputIsIgnoredAndGyroCannotStart() {
        val controller = Controller()
        controller.move(10f, 20f)
        controller.click()
        controller.flying(true)
        assertFalse(controller.uiState.value.flying)
        assertTrue(controller.reports().isEmpty())
    }

    @Test fun connectingDoesNotEnableGyroAndCalibrationResetsOnEnable() {
        val controller = Controller()
        controller.connect()
        assertFalse(controller.uiState.value.flying)
        controller.flying(true)
        assertTrue(controller.uiState.value.flying)
        assertFalse(controller.uiState.value.calibrated)
    }

    @Test fun clickingDuringDragReleasesBeforeNewClick() {
        val controller = Controller()
        controller.connect()
        controller.toggleDrag()
        controller.reports()
        controller.click()
        assertEquals(listOf<Byte>(0, 1, 0), controller.reports().map { it[0] })
        assertFalse(controller.uiState.value.dragging)
    }
}
