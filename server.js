import express from "express";
import path from "path";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = process.env.PORT || 3000;

const simulatorConfig = {
  runtime: "offline",
  mode: "sandbox",
  physics: "simplified",
  visualStyle: "schematic",
  brandUI: false
};

const simulatorSpec = {
  location: "Jamaica (PSH 5.5, tilt 15–17°)",
  components: [
    "PV panels with series/parallel/series-parallel logic and adjustable sizes",
    "Inverter (3k/6k/8k/12k) with PV, battery, AC in/out, generator input, programmable modes",
    "Battery types: lead acid 6V, gel 12V, lithium 48V/51.2V with SOC + charge/discharge state",
    "ATS/MTS (63A/100A/125A), generator split phase L1-N-L2-PE",
    "Protection: VAC/VDC SPD 1000V, breakers (AC, PV, battery) with adjustable poles/amps",
    "Distribution panels (8/12 way) with breaker snap points, L1/L2 feeds, neutral/ground bars",
    "Loads: lights, motors, contactors, relays, plugs, GFCI, RCD, MCB/MCCB, draw boxes",
    "Charge controllers: PWM + MPPT with configurable voltage/amps"
  ],
  behaviors: [
    "Drag/drop components onto empty canvas and snap wires to terminals",
    "Yellow current-flow animation on wires (DC one direction, AC back-and-forth)",
    "Hover/click reveals live voltage, watts, amps per component/terminal",
    "Sun dial time-of-day slider drives irradiance and PV output"
  ],
  safety: [
    "Grounding terminals on all required equipment; color-coded DC/AC wiring",
    "Fault detection for overload, short circuit, ground fault with warning lights",
    "Contactor logic, ATS/MTS behavior, and breaker switching interactions"
  ],
  faults: [
    "Overload: I > circuit capacity sustained",
    "Short circuit: R < 0.1 Ω instantaneous trip",
    "Ground fault: I_line ≠ I_neutral (ΔI)"
  ]
};

app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

app.get("/api/simulator-config", (_req, res) => {
  res.json({ simulatorConfig, simulatorSpec });
});

app.post("/api/generate-brief", (req, res) => {
  const input = {
    goal: req.body?.goal || "Solar + electrical training simulator.",
    users: req.body?.users || "Installers and trainees.",
    constraints: req.body?.constraints || "Browser-based, interactive.",
    notes: req.body?.notes || "",
    runtime: req.body?.runtime || simulatorConfig.runtime,
    mode: req.body?.mode || simulatorConfig.mode,
    physics: req.body?.physics || simulatorConfig.physics,
    visualStyle: req.body?.visualStyle || simulatorConfig.visualStyle,
    brandUI: Boolean(req.body?.brandUI)
  };

  const understanding = [
    input.goal,
    "Interactive canvas with drag/drop wiring and live data panels.",
    "Supports PV, inverters, batteries, charge controllers, ATS/MTS, protection, meters, and loads.",
    "Simulates PV array logic (series/parallel), inverter modes, SOC changes, and time-of-day impact.",
    "Includes fault logic with visual status indicators and grounded wiring rules.",
    `Regional config: ${simulatorSpec.location}.`,
    `Runtime: ${input.runtime}.`,
    `Simulator mode: ${input.mode}.`,
    `Physics depth: ${input.physics}.`,
    `Visual style: ${input.visualStyle}.`,
    `Brand UI: ${input.brandUI ? "branded" : "generic"}.`
  ];

  const recommendations = [
    "Start with an MVP: canvas, wire snapping, PV + inverter + battery + load loop.",
    "Define a shared component schema (ports, terminals, properties, logic).",
    "Implement a simulation tick (e.g., 10–20 Hz) to update flows + SOC.",
    "Add fault detection and visual cues after baseline power-flow works.",
    "Phase advanced components (motors, relays, MCCB, ATS/MTS) after core is stable."
  ];

  const questions = [
    "Do you want single-user offline mode only, or multi-user/cloud saves?",
    "Should the simulator include step-by-step training scenarios and scoring?",
    "What level of electrical accuracy is required (simplified vs standards-based)?",
    "Preferred component library look: realistic images or schematic symbols?",
    "Any specific inverter brands/UI you want replicated in the details panel?"
  ];

  const prompt = [
    "Act as a senior product engineer. Build a browser-based solar + electrical training simulator.",
    "",
    `Goal: ${input.goal}`,
    `Target users: ${input.users}`,
    `Constraints: ${input.constraints}`,
    `Region spec: ${simulatorSpec.location}`,
    `Runtime: ${input.runtime}`,
    `Mode: ${input.mode}`,
    `Physics: ${input.physics}`,
    `Visual style: ${input.visualStyle}`,
    `Brand UI: ${input.brandUI ? "branded" : "generic"}`,
    "",
    "Core UI:",
    "- Empty canvas with retractable component library on the left",
    "- Detail/properties panel on the right (editable sizes/types, programming modes)",
    "- Top bar with Play button + sun dial time-of-day slider",
    "",
    "Components (at minimum):",
    ...simulatorSpec.components.map(item => `- ${item}`),
    "",
    "Behavior & logic:",
    ...simulatorSpec.behaviors.map(item => `- ${item}`),
    "",
    "Safety & faults:",
    ...simulatorSpec.safety.map(item => `- ${item}`),
    "Fault thresholds:",
    ...simulatorSpec.faults.map(item => `- ${item}`),
    "",
    "Other rules:",
    "- Wire color codes: DC red/black/green, AC red/black/green",
    "- Inverter types: grid-tie/off-grid/hybrid with programmable modes (SBU, solar-first, battery-first)",
    "- Battery SOC must rise/fall based on PV, load, and time-of-day",
    "- Display live values (V, A, W) on hover/click",
    "",
    "Output:",
    "- Proposed architecture + data model",
    "- Phase plan (MVP → advanced)",
    "- Core simulation loop pseudocode"
  ];

  if (input.notes) {
    prompt.push(`Notes: ${input.notes}`);
  }

  res.json({
    input,
    understanding,
    recommendations,
    questions,
    prompt: prompt.join("\n")
  });
});

app.listen(PORT, () => {
  console.log(`Server running on http://localhost:${PORT}`);
});
