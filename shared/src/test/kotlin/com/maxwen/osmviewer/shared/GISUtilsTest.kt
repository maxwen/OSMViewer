package com.maxwen.osmviewer.shared

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class GISUtilsTest {
    @Test
    fun testPolygonStringConversion() {
        /*
  ('Linestring', 'LINESTRING(0 0, 1 1, 2 1, 2 2)'),
  ('Polygon', 'POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))'),*/

        var geomString ="LINESTRING(0 0, 1 1, 2 1, 2 2)"
        var polygons = GISUtils.createCoordsFromPolygonString(geomString).toString()
        assertTrue(polygons.equals("[[0.0, 0.0], [1.0, 1.0], [2.0, 1.0], [2.0, 2.0]]"))

        geomString ="POLYGON((0 0, 1 0, 1 1, 0 1, 0 0))"
        polygons = GISUtils.createCoordsFromPolygonString(geomString).toString()
        assertTrue(polygons.equals("[[[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0], [0.0, 0.0]]]"))
    }
}