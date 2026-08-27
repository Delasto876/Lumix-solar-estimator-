# Phase 1 Spec — Electrical Simulator Frontend

## Goal
Deliver a Phase-1, frontend-only simulator with a locked layout, SVG canvas wiring, a minimal component library, and exportable scene JSON. No backend work is included in Phase 1.

## Layout (Locked)
- **TopBar:** 64px height, fixed at the top of the app.
- **Main Area:** horizontal split
  - **Canvas:** 70% width (left).
  - **Details Panel:** 30% width (right).
- **Bottom Status:** 32px height.

## Canvas & Components
- Canvas is an SVG surface.
- Components render as **schematic-style blocks** with a title and terminals.
- Terminals are small circles with:
  - `id`: unique per component
  - `type`: `AC`, `DC`, or `PE`
- Components are draggable (snap-free) and store x,y positions.

### Default Components on Canvas
- **Hybrid Inverter** centered.
- **PV Array** above-left.
- **Battery** below-left.
- **Grid Source** left.
- **Generator Source** left-below grid.
- **Load Panel** right of inverter.

## Wiring
- **Modes:** Select, Wire, Delete.
- **Wire mode:**
  - Click terminal A then terminal B → create wire.
  - Wire stored as:
    ```json
    {
      "id": "wire-1",
      "from": {"componentId": "inv-1", "terminalId": "dc-in+"},
      "to": {"componentId": "bat-1", "terminalId": "dc-out+"},
      "valid": true,
      "color": "#22c55e"
    }
    ```
- **Validation rules:**
  - DC-to-DC only
  - AC-to-AC only
  - PE-to-PE only
  - Otherwise `valid=false` and wire is **gray** with **red outline**.

## Selection & Details Panel
- Clicking a component selects it.
- Details panel displays:
  - Component type
  - Name
  - Editable properties (simple form)
  - Terminal list
- Include basic PV/Battery placeholders (sliders for power/SOC). No heavy physics.

## Export JSON
- Export button in TopBar generates and downloads a JSON scene file containing:
  - Components
  - Terminals
  - Wires
  - Selected component id
  - Canvas metadata

## Colors & Styling
- AC wires: `#2563eb`
- DC wires: `#22c55e`
- PE wires: `#6b7280`
- Invalid wires: **gray** stroke + **red outline**
- Terminal colors:
  - AC terminals: `#2563eb`
  - DC terminals: `#22c55e`
  - PE terminals: `#6b7280`

## Component Library (Phase 1)
- Hybrid Inverter
- PV Array
- Battery
- Grid Source
- Generator Source
- Load Panel

## Notes
- Frontend only, no backend work in Phase 1.
- Use React state only (no heavy external libraries).
- Keep layout locked to the specified dimensions and split.
