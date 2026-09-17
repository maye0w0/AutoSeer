package com.autoseer.libautomata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class GeometryTest {
    private val normalized = Size(1280, 720)
    private val device = Size(1920, 1080) // 1.5x

    @Test
    fun locationTransformScalesUp() {
        val n = Location(100, 200)
        val d = n.transform(normalized, device)
        assertEquals(150, d.x)
        assertEquals(300, d.y)
    }

    @Test
    fun locationTransformIsIdentityWhenSameSize() {
        val n = Location(640, 360)
        assertEquals(n, n.transform(normalized, normalized))
    }

    @Test
    fun regionCenterAndTransform() {
        val r = Region(0, 0, 1280, 720)
        assertEquals(Location(640, 360), r.center)
        val d = r.transform(normalized, device)
        assertEquals(Region(0, 0, 1920, 1080), d)
    }

    @Test
    fun regionContains() {
        val r = Region(10, 10, 100, 100)
        assertTrue(r.contains(Location(10, 10)))
        assertTrue(r.contains(Location(109, 109)))
        assertFalse(r.contains(Location(110, 110)))
    }
}
