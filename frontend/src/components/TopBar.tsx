import { Mode } from "../types";

type TopBarProps = {
  mode: Mode;
  onModeChange: (mode: Mode) => void;
  onExport: () => void;
};

const TopBar = ({ mode, onModeChange, onExport }: TopBarProps) => {
  return (
    <header className="top-bar">
      <h1>Electrical Simulator – Phase 1</h1>
      <div className="toolbar">
        <span className="mode-pill">Mode: {mode}</span>
        <button
          className={mode === "select" ? "secondary" : ""}
          onClick={() => onModeChange("select")}
          type="button"
        >
          Select
        </button>
        <button
          className={mode === "wire" ? "secondary" : ""}
          onClick={() => onModeChange("wire")}
          type="button"
        >
          Wire
        </button>
        <button
          className={mode === "delete" ? "secondary" : ""}
          onClick={() => onModeChange("delete")}
          type="button"
        >
          Delete
        </button>
        <button onClick={onExport} type="button">Export JSON</button>
      </div>
    </header>
  );
};

export default TopBar;
