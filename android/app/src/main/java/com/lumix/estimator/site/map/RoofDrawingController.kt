package com.lumix.estimator.site.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumix.estimator.site.GeoPoint

/**
 * Tracks the roof polygon currently being traced. Every vertex is a real map coordinate
 * (tap position converted to lat/lng by the caller) — this never stores screen pixels, so the
 * polygon stays anchored to the property regardless of how the map is panned or zoomed.
 */
class RoofDrawingController {
    var isDrawing by mutableStateOf(false)
        private set

    private val _vertices = mutableStateListOf<GeoPoint>()
    val vertices: List<GeoPoint> get() = _vertices

    private val redoStack = mutableStateListOf<GeoPoint>()

    fun startDrawing() {
        isDrawing = true
        _vertices.clear()
        redoStack.clear()
    }

    fun addVertex(point: GeoPoint) {
        if (!isDrawing) return
        _vertices.add(point)
        redoStack.clear()
    }

    fun undo() {
        if (_vertices.isNotEmpty()) redoStack.add(_vertices.removeAt(_vertices.lastIndex))
    }

    fun redo() {
        if (redoStack.isNotEmpty()) _vertices.add(redoStack.removeAt(redoStack.lastIndex))
    }

    fun clear() {
        _vertices.clear()
        redoStack.clear()
    }

    /** Ends the current trace and returns the closed polygon's vertices. */
    fun finishDrawing(): List<GeoPoint> {
        isDrawing = false
        return _vertices.toList()
    }

    fun cancelDrawing() {
        isDrawing = false
        _vertices.clear()
        redoStack.clear()
    }
}
