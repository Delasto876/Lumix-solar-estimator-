package com.lumix.estimator.site.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lumix.estimator.site.GeoPoint

/**
 * "ROOF DRAWING... The drawing tool must be an actual interactive drawing tool... Allow: MOVE
 * POINT, DELETE POINT, ADD POINT, CLEAR ROOF, REDRAW... The polygon must remain editable" —
 * (2026-08-18). Renamed/expanded from the earlier `RoofDrawingController` (which only supported
 * tap-to-add + undo/clear + a one-shot finish): every vertex is still a real map coordinate, never
 * a screen pixel, so the polygon stays anchored to the property regardless of pan/zoom — but this
 * version also supports editing an already-closed polygon (moving/deleting/inserting a vertex) and
 * loading a previously-saved roof plane back in for a full redraw, not just a fresh trace.
 */
class RoofDrawingService {
    var isDrawing by mutableStateOf(false)
        private set

    /** True once at least one vertex exists and [isDrawing] is false — an already-closed polygon the caller can still move/delete/insert points on, per the "polygon must remain editable" requirement. */
    var isEditing by mutableStateOf(false)
        private set

    private val _vertices = mutableStateListOf<GeoPoint>()
    val vertices: List<GeoPoint> get() = _vertices

    private val redoStack = mutableStateListOf<GeoPoint>()

    /** Which vertex (if any) is currently selected for a MOVE — the next map tap relocates it instead of adding a new point. */
    var selectedVertexIndex by mutableStateOf<Int?>(null)
        private set

    fun startDrawing() {
        isDrawing = true
        isEditing = false
        selectedVertexIndex = null
        _vertices.clear()
        redoStack.clear()
    }

    /** ADD POINT while actively tracing. */
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

    /** CLEAR ROOF. */
    fun clear() {
        _vertices.clear()
        redoStack.clear()
        selectedVertexIndex = null
    }

    /** Ends the current trace and returns the closed polygon's vertices. */
    fun finishDrawing(): List<GeoPoint> {
        isDrawing = false
        return _vertices.toList()
    }

    fun cancelDrawing() {
        isDrawing = false
        isEditing = false
        selectedVertexIndex = null
        _vertices.clear()
        redoStack.clear()
    }

    /**
     * Loads an already-traced (or already-saved) polygon back into the editable buffer —
     * REDRAW/edit-existing, not a fresh trace. [isDrawing] stays false ([isEditing] becomes true)
     * so the caller can distinguish "still placing the first N points" UI from "editing a
     * complete shape" UI.
     */
    fun startEditing(existingVertices: List<GeoPoint>) {
        isDrawing = false
        isEditing = true
        selectedVertexIndex = null
        _vertices.clear()
        _vertices.addAll(existingVertices)
        redoStack.clear()
    }

    /** Selects a vertex by index for MOVE POINT — the next call to [moveSelectedVertexTo] relocates it. */
    fun selectVertex(index: Int) {
        if (index in _vertices.indices) selectedVertexIndex = index
    }

    fun clearSelection() {
        selectedVertexIndex = null
    }

    /** MOVE POINT: relocates the currently selected vertex to a new map coordinate. */
    fun moveSelectedVertexTo(point: GeoPoint) {
        val index = selectedVertexIndex ?: return
        if (index !in _vertices.indices) return
        _vertices[index] = point
    }

    /** DELETE POINT. Refuses to drop below 3 vertices — a polygon needs at least a triangle. */
    fun deleteVertex(index: Int) {
        if (index !in _vertices.indices) return
        if (_vertices.size <= 3) return
        _vertices.removeAt(index)
        if (selectedVertexIndex == index) selectedVertexIndex = null
    }

    /** ADD POINT while editing an already-closed polygon — inserts after [afterIndex] (wrapping to the start when it's the last vertex), rather than only ever appending at the end. */
    fun insertVertexAfter(afterIndex: Int, point: GeoPoint) {
        if (_vertices.isEmpty()) {
            _vertices.add(point)
            return
        }
        val insertAt = (afterIndex + 1).coerceIn(0, _vertices.size)
        _vertices.add(insertAt, point)
    }

    /** Commits edits made via [startEditing] and returns the resulting vertex list. */
    fun finishEditing(): List<GeoPoint> {
        isEditing = false
        selectedVertexIndex = null
        return _vertices.toList()
    }
}
