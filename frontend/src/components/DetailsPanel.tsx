import { ComponentInstance } from "../types";

type DetailsPanelProps = {
  selected: ComponentInstance | null;
  onUpdate: (updated: ComponentInstance) => void;
};

const DetailsPanel = ({ selected, onUpdate }: DetailsPanelProps) => {
  if (!selected) {
    return (
      <aside className="details-panel">
        <h2>Details</h2>
        <p>Select a component to view details.</p>
      </aside>
    );
  }

  const updateProperty = (key: string, value: string | number) => {
    onUpdate({
      ...selected,
      properties: {
        ...selected.properties,
        [key]: value
      }
    });
  };

  return (
    <aside className="details-panel">
      <h2>{selected.type}</h2>
      <div className="panel-section">
        <label>Name</label>
        <input
          type="text"
          value={selected.name}
          onChange={(event) => onUpdate({ ...selected, name: event.target.value })}
        />
      </div>
      <div className="panel-section">
        <h3>Properties</h3>
        {Object.entries(selected.properties).map(([key, value]) => {
          if (typeof value === "number") {
            const isPercent = key.toLowerCase().includes("soc");
            return (
              <div key={key} className="panel-section">
                <label>{key}</label>
                {isPercent ? (
                  <input
                    type="range"
                    min={0}
                    max={100}
                    value={value}
                    onChange={(event) => updateProperty(key, Number(event.target.value))}
                  />
                ) : (
                  <input
                    type="number"
                    value={value}
                    onChange={(event) => updateProperty(key, Number(event.target.value))}
                  />
                )}
              </div>
            );
          }

          return (
            <div key={key} className="panel-section">
              <label>{key}</label>
              <input
                type="text"
                value={value}
                onChange={(event) => updateProperty(key, event.target.value)}
              />
            </div>
          );
        })}
      </div>
      <div className="panel-section">
        <h3>Terminals</h3>
        <ul>
          {selected.terminals.map((terminal) => (
            <li key={terminal.id}>
              {terminal.label} ({terminal.type})
            </li>
          ))}
        </ul>
      </div>
    </aside>
  );
};

export default DetailsPanel;
