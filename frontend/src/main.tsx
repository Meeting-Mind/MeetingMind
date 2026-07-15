import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "./App";
import "./styles/landing.css";
import "./styles/app.css";
import "./styles/base.css";

const SCALED_SHELL_SELECTOR =
  ".workspace-catalog-shell, .app-shell, .lk-live-room-shell, .report-agent-page, .meeting-prejoin-shell, .meeting-access-shell";

// ponytail: Chromium can leave the scale()'d shell composited at the pre-resize size after an
// OS window snap/resize; forcing a transform toggle invalidates the stale layer so it repaints.
let resizeRepaintTimer: number | undefined;
window.addEventListener("resize", () => {
  window.clearTimeout(resizeRepaintTimer);
  resizeRepaintTimer = window.setTimeout(() => {
    const shell = document.querySelector<HTMLElement>(SCALED_SHELL_SELECTOR);
    if (!shell) {
      return;
    }
    shell.style.transform = "none";
    void shell.offsetHeight;
    shell.style.transform = "";
  }, 120);
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <App />
    </BrowserRouter>
  </React.StrictMode>,
);
