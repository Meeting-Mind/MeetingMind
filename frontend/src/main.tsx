import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import "./index.css";
import "./styles/tokens.css";
import "./components/common/common.css";
import "./styles/app.css";
import "./styles/report.css";
import "./styles/base.css";

const SCALED_SHELL_SELECTOR =
  ".workspace-catalog-shell, .app-shell, .lk-live-room-shell, .meeting-prejoin-shell, .meeting-access-shell";

// ponytail: Chromium can leave the scale()'d shell composited at the pre-resize size after an
// OS window snap/resize; forcing a transform toggle invalidates the stale layer so it repaints.
// window "resize" misses OS display-scale changes / monitor moves, so visualViewport "resize" is
// also wired up since Chromium fires that one more reliably for those cases.
let resizeRepaintTimer: number | undefined;
const repaintScaledShell = () => {
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
};
window.addEventListener("resize", repaintScaledShell);
window.visualViewport?.addEventListener("resize", repaintScaledShell);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
