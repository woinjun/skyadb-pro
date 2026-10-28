package com.sky22333.skyadb.data

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickActionOrderTest {
    @Test fun missingAndInvalidIdsAreMigratedWithoutLosingOrder() {
        val restored = QuickActionOrder.normalize(listOf("pointer", "unknown", "apps", "pointer"))
        assertEquals(listOf("pointer", "apps"), restored.take(2))
        assertEquals(QuickActionOrder.defaults.toSet(), restored.toSet())
        assertEquals(QuickActionOrder.defaults.size, restored.size)
    }

    @Test fun movingTouchpadToFrontPreservesAllOtherItems() {
        val moved = QuickActionOrder.move(QuickActionOrder.defaults, "pointer", -100)
        assertEquals("pointer", moved.first())
        assertEquals(QuickActionOrder.defaults.filter { it != "pointer" }, moved.drop(1))
    }

    @Test fun emptySavedOrderRestoresDefaults() {
        assertEquals(QuickActionOrder.defaults, QuickActionOrder.normalize(listOf("")))
    }

    @Test fun boundariesAndUnknownIdsAreSafe() {
        assertEquals(QuickActionOrder.defaults, QuickActionOrder.move(QuickActionOrder.defaults, "apps", -1))
        assertEquals(QuickActionOrder.defaults, QuickActionOrder.move(QuickActionOrder.defaults, "screenshot", 1))
        assertEquals(QuickActionOrder.defaults, QuickActionOrder.move(QuickActionOrder.defaults, "missing", 1))
    }
}
