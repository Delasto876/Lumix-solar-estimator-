const form = document.getElementById("brief-form");
const understandingEl = document.getElementById("understanding");
const recommendationsEl = document.getElementById("recommendations");
const questionsEl = document.getElementById("questions");
const promptEl = document.getElementById("prompt");
const runtimePill = document.getElementById("runtime-pill");
const modePill = document.getElementById("mode-pill");

const defaults = {
  goal: "Solar & electrical training simulator with interactive wiring and live power-flow.",
  users: "Electrical trainees, solar installers, instructors.",
  constraints: "Runs in-browser, drag-and-drop canvas, real-time simulation logic.",
  notes: "Use Jamaica PSH 5.5, tilt 15–17°, enforce safety logic."
};

function fillDefaults(config) {
  document.getElementById("goal").value = defaults.goal;
  document.getElementById("users").value = defaults.users;
  document.getElementById("constraints").value = defaults.constraints;
  document.getElementById("notes").value = defaults.notes;
  document.getElementById("runtime").value = config.runtime;
  document.getElementById("mode").value = config.mode;
  document.getElementById("physics").value = config.physics;
  document.getElementById("visualStyle").value = config.visualStyle;
  document.getElementById("brandUI").value = String(config.brandUI);
  runtimePill.textContent = `Runtime: ${config.runtime}`;
  modePill.textContent = `Mode: ${config.mode}`;
}

function renderList(target, items) {
  target.innerHTML = "";
  items.forEach(item => {
    const li = document.createElement("li");
    li.textContent = item;
    target.appendChild(li);
  });
}

async function loadConfig() {
  const response = await fetch("/api/simulator-config");
  const data = await response.json();
  fillDefaults(data.simulatorConfig);
}

async function handleSubmit(event) {
  event.preventDefault();
  const payload = {
    goal: document.getElementById("goal").value.trim(),
    users: document.getElementById("users").value.trim(),
    constraints: document.getElementById("constraints").value.trim(),
    notes: document.getElementById("notes").value.trim(),
    runtime: document.getElementById("runtime").value,
    mode: document.getElementById("mode").value,
    physics: document.getElementById("physics").value,
    visualStyle: document.getElementById("visualStyle").value,
    brandUI: document.getElementById("brandUI").value === "true"
  };

  const response = await fetch("/api/generate-brief", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });

  const data = await response.json();
  renderList(understandingEl, data.understanding);
  renderList(recommendationsEl, data.recommendations);
  renderList(questionsEl, data.questions);
  promptEl.textContent = data.prompt;
  runtimePill.textContent = `Runtime: ${payload.runtime}`;
  modePill.textContent = `Mode: ${payload.mode}`;
}

form.addEventListener("submit", handleSubmit);
loadConfig();
