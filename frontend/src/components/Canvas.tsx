import { useMemo, useRef, useState } from "react";
import { ComponentInstance, Mode, Terminal, Wire } from "../types";

type CanvasProps = {
  components: ComponentInstance[];
  wires: Wire[];
  mode: Mode;
  selectedId: string | null;
  onSelect: (id: string | null) => void;
  onMove: (id: string, x: number, y: number) => void;
  onAddWire: (wire: Wire) => void;
  onDeleteWire: (wireId: string) => void;
};

const CANVAS_WIDTH = 900;
const CANVAS_HEIGHT = 520;
const COMPONENT_WIDTH = 180;
const COMPONENT_HEIGHT = 120;

const terminalColor = (type: Terminal["type"]) => {
  if (type === "AC") return "#2563eb";
  if (type === "DC") return "#22c55e";
  return "#6b7280";
};

const wireColor = (type: Terminal["type"]) => {
  if (type === "AC") return "#2563eb";
  if (type === "DC") return "#22c55e";
  return "#6b7280";
};

const Canvas = ({
  components,
  wires,
  mode,
  selectedId,
  onSelect,
  onMove,
  onAddWire,
  onDeleteWire
}: CanvasProps) => {
  const [dragId, setDragId] = useState<string | null>(null);
  const [dragOffset, setDragOffset] = useState({ x: 0, y: 0 });
  const [pendingTerminal, setPendingTerminal] = useState<{
    componentId: string;
    terminal: Terminal;
  } | null>(null);
  const svgRef = useRef<SVGSVGElement | null>(null);

  const componentById = useMemo(() => {
    const map = new Map<string, ComponentInstance>();
    components.forEach((component) => map.set(component.id, component));
    return map;
  }, [components]);

  const handlePointerDown = (
    event: React.PointerEvent<SVGGElement>,
    component: ComponentInstance
  ) => {
    if (mode !== "select") return;
    const target = event.currentTarget;
    const rect = target.getBoundingClientRect();
    setDragId(component.id);
    setDragOffset({
      x: event.clientX - rect.left,
      y: event.clientY - rect.top
    });
    onSelect(component.id);
  };

  const handlePointerMove = (event: React.PointerEvent<SVGSVGElement>) => {
    if (!dragId || mode !== "select") return;
    const svg = svgRef.current;
    if (!svg) return;
    const rect = svg.getBoundingClientRect();
    const x = event.clientX - rect.left - dragOffset.x;
    const y = event.clientY - rect.top - dragOffset.y;
    onMove(dragId, x, y);
  };

  const handlePointerUp = () => {
    setDragId(null);
  };

  const getTerminalPosition = (component: ComponentInstance, terminal: Terminal, index: number) => {
    const spacing = COMPONENT_HEIGHT / (component.terminals.length + 1);
    return {
      x: component.x + COMPONENT_WIDTH - 8,
      y: component.y + spacing * (index + 1)
    };
  };

  const handleTerminalClick = (component: ComponentInstance, terminal: Terminal) => {
    if (mode === "delete") {
      const toRemove = wires.filter(
        (wire) =>
          (wire.from.componentId === component.id && wire.from.terminalId === terminal.id) ||
          (wire.to.componentId === component.id && wire.to.terminalId === terminal.id)
      );
      toRemove.forEach((wire) => onDeleteWire(wire.id));
      return;
    }

    if (mode !== "wire") return;

    if (!pendingTerminal) {
      setPendingTerminal({ componentId: component.id, terminal });
      return;
    }

    const valid = pendingTerminal.terminal.type === terminal.type;
    const wire: Wire = {
      id: `wire-${Date.now()}`,
      from: { componentId: pendingTerminal.componentId, terminalId: pendingTerminal.terminal.id },
      to: { componentId: component.id, terminalId: terminal.id },
      valid,
      color: valid ? wireColor(terminal.type) : "#9ca3af"
    };
    onAddWire(wire);
    setPendingTerminal(null);
  };

  return (
    <svg
      ref={svgRef}
      width="100%"
      height="100%"
      viewBox={`0 0 ${CANVAS_WIDTH} ${CANVAS_HEIGHT}`}
      onPointerMove={handlePointerMove}
      onPointerUp={handlePointerUp}
      onPointerLeave={handlePointerUp}
    >
      {wires.map((wire) => {
        const fromComponent = componentById.get(wire.from.componentId);
        const toComponent = componentById.get(wire.to.componentId);
        if (!fromComponent || !toComponent) return null;
        const fromTerminal = fromComponent.terminals.find((t) => t.id === wire.from.terminalId);
        const toTerminal = toComponent.terminals.find((t) => t.id === wire.to.terminalId);
        if (!fromTerminal || !toTerminal) return null;
        const fromIndex = fromComponent.terminals.indexOf(fromTerminal);
        const toIndex = toComponent.terminals.indexOf(toTerminal);
        const fromPos = getTerminalPosition(fromComponent, fromTerminal, fromIndex);
        const toPos = getTerminalPosition(toComponent, toTerminal, toIndex);
        const midX = (fromPos.x + toPos.x) / 2;
        const path = `M ${fromPos.x} ${fromPos.y} C ${midX} ${fromPos.y}, ${midX} ${toPos.y}, ${toPos.x} ${toPos.y}`;

        return (
          <g key={wire.id}>
            {!wire.valid && <path className="wire invalid-outline" d={path} />}
            <path className={`wire ${wire.valid ? "" : "invalid"}`} stroke={wire.color} d={path} />
          </g>
        );
      })}

      {components.map((component) => (
        <g
          key={component.id}
          transform={`translate(${component.x}, ${component.y})`}
          onPointerDown={(event) => handlePointerDown(event, component)}
        >
          <rect
            className="component-box"
            width={COMPONENT_WIDTH}
            height={COMPONENT_HEIGHT}
            stroke={component.id === selectedId ? "#1d4ed8" : undefined}
          />
          <text className="component-title" x={12} y={20}>
            {component.name}
          </text>
          {component.terminals.map((terminal, index) => {
            const pos = getTerminalPosition(component, terminal, index);
            return (
              <g key={terminal.id}>
                <circle
                  className={`terminal ${terminal.type.toLowerCase()}`}
                  cx={pos.x}
                  cy={pos.y}
                  r={6}
                  onClick={() => handleTerminalClick(component, terminal)}
                />
                <text x={pos.x - 70} y={pos.y + 4} fontSize={10}>
                  {terminal.label}
                </text>
              </g>
            );
          })}
        </g>
      ))}
    </svg>
  );
};

export default Canvas;
