import { useMemo, useState } from "react";
import Canvas from "./components/Canvas";
import DetailsPanel from "./components/DetailsPanel";
import TopBar from "./components/TopBar";
import { initialComponents, initialWires } from "./data/initialScene";
import { ComponentInstance, Mode, Wire } from "./types";

const App = () => {
  const [components, setComponents] = useState<ComponentInstance[]>(initialComponents);
  const [wires, setWires] = useState<Wire[]>(initialWires);
  const [mode, setMode] = useState<Mode>("select");
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const selectedComponent = useMemo(
    () => components.find((component) => component.id === selectedId) || null,
    [components, selectedId]
  );

  const updateComponent = (updated: ComponentInstance) => {
    setComponents((prev) => prev.map((component) => (component.id === updated.id ? updated : component)));
  };

  const moveComponent = (id: string, x: number, y: number) => {
    setComponents((prev) =>
      prev.map((component) =>
        component.id === id ? { ...component, x: Math.max(0, x), y: Math.max(0, y) } : component
      )
    );
  };

  const addWire = (wire: Wire) => {
    setWires((prev) => [...prev, wire]);
  };

  const deleteWire = (wireId: string) => {
    setWires((prev) => prev.filter((wire) => wire.id !== wireId));
  };

  const handleExport = () => {
    const payload = {
      metadata: {
        exportedAt: new Date().toISOString()
      },
      mode,
      selectedId,
      components,
      wires
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "electrical-simulator-scene.json";
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="app-shell">
      <TopBar mode={mode} onModeChange={setMode} onExport={handleExport} />
      <main className="main">
        <div className="canvas-wrap">
          <Canvas
            components={components}
            wires={wires}
            mode={mode}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onMove={moveComponent}
            onAddWire={addWire}
            onDeleteWire={deleteWire}
          />
        </div>
        <DetailsPanel selected={selectedComponent} onUpdate={updateComponent} />
      </main>
      <footer className="bottom-status">
        Status: {mode.toUpperCase()} mode • Components: {components.length} • Wires: {wires.length}
      </footer>
    </div>
  );
};

export default App;
