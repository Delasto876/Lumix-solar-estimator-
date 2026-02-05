export type TerminalType = "AC" | "DC" | "PE";

export type Terminal = {
  id: string;
  label: string;
  type: TerminalType;
};

export type ComponentInstance = {
  id: string;
  type: string;
  name: string;
  x: number;
  y: number;
  properties: Record<string, number | string>;
  terminals: Terminal[];
};

export type WireEndpoint = {
  componentId: string;
  terminalId: string;
};

export type Wire = {
  id: string;
  from: WireEndpoint;
  to: WireEndpoint;
  valid: boolean;
  color: string;
};

export type Mode = "select" | "wire" | "delete";
