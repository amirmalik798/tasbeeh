package com.amirmalik.tasbeeh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasbeehTapTargetTest {

    @Test
    fun acceptsCenterOfCounter() {
        assertTrue(isInsideTasbeehTapTarget(x = 200f, y = 170f, width = 400f, height = 400f))
    }

    @Test
    fun acceptsOuterBeadArea() {
        assertTrue(isInsideTasbeehTapTarget(x = 340f, y = 170f, width = 400f, height = 400f))
    }

    @Test
    fun rejectsInvisibleCanvasCorner() {
        assertFalse(isInsideTasbeehTapTarget(x = 0f, y = 0f, width = 400f, height = 400f))
    }

    @Test
    fun rejectsTapOutsideCircleAboveCounter() {
        assertFalse(isInsideTasbeehTapTarget(x = 200f, y = 0f, width = 400f, height = 400f))
    }

    @Test
    fun rejectsInvalidCanvasSize() {
        assertFalse(isInsideTasbeehTapTarget(x = 0f, y = 0f, width = 0f, height = 400f))
        assertFalse(isInsideTasbeehTapTarget(x = 0f, y = 0f, width = 400f, height = 0f))
    }
}
