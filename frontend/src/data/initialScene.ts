import { ComponentInstance, Wire } from "../types";

export const initialComponents: ComponentInstance[] = [
  {
    id: "inv-1",
    type: "Hybrid Inverter",
    name: "Hybrid Inverter",
    x: 380,
    y: 220,
    properties: { ratingKw: 6, mode: "Hybrid" },
    terminals: [
      { id: "dc-in+", label: "PV+", type: "DC" },
      { id: "dc-in-", label: "PV-", type: "DC" },
      { id: "bat+", label: "BAT+", type: "DC" },
      { id: "bat-", label: "BAT-", type: "DC" },
      { id: "ac-in", label: "AC IN", type: "AC" },
      { id: "ac-out", label: "AC OUT", type: "AC" },
      { id: "pe", label: "PE", type: "PE" }
    ]
  },
  {
    id: "pv-1",
    type: "PV Array",
    name: "PV Array",
    x: 120,
    y: 80,
    properties: { watts: 1200 },
    terminals: [
      { id: "pv+", label: "PV+", type: "DC" },
      { id: "pv-", label: "PV-", type: "DC" },
      { id: "pe", label: "PE", type: "PE" }
    ]
  },
  {
    id: "bat-1",
    type: "Battery",
    name: "Battery Bank",
    x: 120,
    y: 320,
    properties: { soc: 60 },
    terminals: [
      { id: "bat+", label: "BAT+", type: "DC" },
      { id: "bat-", label: "BAT-", type: "DC" },
      { id: "pe", label: "PE", type: "PE" }
    ]
  },
  {
    id: "grid-1",
    type: "Grid Source",
    name: "Grid",
    x: 120,
    y: 200,
    properties: { voltage: 120 },
    terminals: [
      { id: "l1", label: "L1", type: "AC" },
      { id: "n", label: "N", type: "AC" },
      { id: "pe", label: "PE", type: "PE" }
    ]
  },
  {
    id: "gen-1",
    type: "Generator",
    name: "Generator",
    x: 120,
    y: 420,
    properties: { voltage: 240 },
    terminals: [
      { id: "l1", label: "L1", type: "AC" },
      { id: "l2", label: "L2", type: "AC" },
      { id: "n", label: "N", type: "AC" },
      { id: "pe", label: "PE", type: "PE" }
    ]
  },
  {
    id: "load-1",
    type: "Load Panel",
    name: "Load Panel",
    x: 640,
    y: 220,
    properties: { circuits: 8 },
    terminals: [
      { id: "l1", label: "L1", type: "AC" },
      { id: "l2", label: "L2", type: "AC" },
      { id: "n", label: "N", type: "AC" },
      { id: "pe", label: "PE", type: "PE" }
    ]
  }
];

export const initialWires: Wire[] = [];
