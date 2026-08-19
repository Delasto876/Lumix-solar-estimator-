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

    /**
     * 2026-08-19 (map Part 4, "drag any vertex to correct it... Undo... Redo"): a separate
     * snapshot-based history for the EDITING phase, distinct from [redoStack] above (which is
     * append-only-vertex granularity, correct for the DRAWING phase's tap-to-add-a-point flow but
     * meaningless for editing an already-closed polygon — there's no "last added vertex" to pop
     * once the shape already has all its points). Each entry is a full copy of [_vertices] taken
     * immediately BEFORE a mutating edit (drag, delete, or insert) — cheap and simple for a roof
     * polygon's realistic vertex count (a handful to a few dozen points), and correct by
     * construction for any mutation type without needing a per-action command/diff representation.
     */
    private val editUndoStack = mutableStateListOf<List<GeoPoint>>()
    private val editRedoStack = mutableStateListOf<List<GeoPoint>>()
    val canUndoEdit: Boolean get() = editUndoStack.isNotEmpty()
    val canRedoEdit: Boolean get() = editRedoStack.isNotEmpty()

    /** Which vertex (if any) is currently selected/being dragged — drives the red highlight-circle layer; see [beginVertexDrag]. */
    var selectedVertexIndex by mutableStateOf<Int?>(null)
        private set

    fun startDrawing() {
        isDrawing = true
        isEditing = false
        selectedVertexIndex = null
        _vertices.clear()
        redoStack.clear()
        editUndoStack.clear()
        editRedoStack.clear()
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
        editUndoStack.clear()
        editRedoStack.clear()
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
        editUndoStack.clear()
        editRedoStack.clear()
    }

    /**
     * Loads an already-traced (or already-saved) polygon back into the editable buffer —
     * REDRAW/edit-existing, not a fresh trace. [isDrawing] stays false ([isEditing] becomes true)
     * so the caller can distinguish "still placing the first N points" UI from "editing a
     * complete shape" UI. A fresh edit session starts with empty undo/redo history — corrections
     * made in an earlier editing session on this same roof (already committed via [finishEditing])
     * aren't still-undoable once that session ended.
     */
    fun startEditing(existingVertices: List<GeoPoint>) {
        isDrawing = false
        isEditing = true
        selectedVertexIndex = null
        _vertices.clear()
        _vertices.addAll(existingVertices)
        redoStack.clear()
        editUndoStack.clear()
        editRedoStack.clear()
    }

    /** Selects a vertex by index — drives the red highlight-circle layer. See [beginVertexDrag] for the actual MOVE POINT flow. */
    fun selectVertex(index: Int) {
        if (index in _vertices.indices) selectedVertexIndex = index
    }

    fun clearSelection() {
        selectedVertexIndex = null
    }

    /** Snapshots the current vertex list onto the edit-undo stack and clears the redo stack — call once before any editing mutation (drag/delete/insert), never mid-mutation. */
    private fun pushEditSnapshot() {
        editUndoStack.add(_vertices.toList())
        editRedoStack.clear()
    }

    /**
     * 2026-08-19 (map Part 4): MOVE POINT is now a real press-and-drag gesture (see
     * `SolarSiteMapScreen`'s own touch-handling doc for why), not the old tap-to-select-then-
     * tap-to-relocate flow. [beginVertexDrag] is called once at the start of a drag gesture
     * (touch-down on a vertex) — it takes the ONE undo snapshot for the whole gesture and marks
     * [index] as selected (for the highlight circle); [updateVertexDragPosition] is then called on
     * every subsequent drag-move frame and does NOT push additional snapshots (a drag fires many
     * move events per second — snapshotting each one would make a single correction take many taps
     * to undo). One drag gesture = exactly one undo step, regardless of how far the finger travels.
     */
    fun beginVertexDrag(index: Int) {
        if (index !in _vertices.indices) return
        pushEditSnapshot()
        selectedVertexIndex = index
    }

    /** Live position update during an active drag — see [beginVertexDrag]. No-op if [index] isn't the vertex [beginVertexDrag] started dragging. */
    fun updateVertexDragPosition(index: Int, point: GeoPoint) {
        if (index !in _vertices.indices) return
        if (selectedVertexIndex != index) return
        _vertices[index] = point
    }

    /** Ends the current drag gesture — clears the highlight. The position is already committed live by [updateVertexDragPosition]; nothing left to finalize. */
    fun endVertexDrag() {
        selectedVertexIndex = null
    }

    /** DELETE POINT. Refuses to drop below 3 vertices — a polygon needs at least a triangle. */
    fun deleteVertex(index: Int) {
        if (index !in _vertices.indices) return
        if (_vertices.size <= 3) return
        pushEditSnapshot()
        _vertices.removeAt(index)
        if (selectedVertexIndex == index) selectedVertexIndex = null
    }

    /** ADD POINT while editing an already-closed polygon — inserts after [afterIndex] (wrapping to the start when it's the last vertex), rather than only ever appending at the end. */
    fun insertVertexAfter(afterIndex: Int, point: GeoPoint) {
        pushEditSnapshot()
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

    /** Reverts the most recent editing mutation (drag/delete/insert) — see [pushEditSnapshot]. No-op if [canUndoEdit] is false. */
    fun editUndo() {
        if (editUndoStack.isEmpty()) return
        editRedoStack.add(_vertices.toList())
        val previous = editUndoStack.removeAt(editUndoStack.lastIndex)
        _vertices.clear()
        _vertices.addAll(previous)
        selectedVertexIndex = null
    }

    /** Re-applies the most recently undone editing mutation. No-op if [canRedoEdit] is false. */
    fun editRedo() {
        if (editRedoStack.isEmpty()) return
        editUndoStack.add(_vertices.toList())
        val next = editRedoStack.removeAt(editRedoStack.lastIndex)
        _vertices.clear()
        _vertices.addAll(next)
        selectedVertexIndex = null
    }
}
