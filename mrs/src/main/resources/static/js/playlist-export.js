/* CSV downloads keep the current page and report success only after a CSV response. */
"use strict";

var bound = false;

function notify(message, failed) {
  var container = document.querySelector("[data-export-notices]");
  if (!container) {
    container = document.createElement("div");
    container.className = "toast-container position-fixed top-0 end-0 p-3";
    container.setAttribute("data-export-notices", "");
    document.body.appendChild(container);
  }
  container.replaceChildren();
  var toast = document.createElement("div");
  toast.className = "toast show " + (failed ? "text-bg-danger" : "text-bg-success");
  toast.setAttribute("role", failed ? "alert" : "status");
  toast.setAttribute("aria-live", failed ? "assertive" : "polite");
  toast.setAttribute("aria-atomic", "true");
  var body = document.createElement("div");
  body.className = "toast-body d-flex align-items-center gap-3";
  var text = document.createElement("span");
  text.textContent = message;
  var close = document.createElement("button");
  close.type = "button";
  close.className = "btn-close btn-close-white ms-auto";
  close.setAttribute("aria-label", "Close notification");
  close.addEventListener("click", function () { toast.remove(); });
  body.append(text, close);
  toast.appendChild(body);
  container.appendChild(toast);
  window.setTimeout(function () { toast.remove(); }, 10000);
}

export function initPlaylistExport() {
  if (bound) return;
  bound = true;
  // Delegation also covers playlist pages loaded through shell navigation.
  document.addEventListener("click", async function (event) {
    var link = event.target.closest("a[data-export-playlist]");
    if (!link || event.defaultPrevented || event.button !== 0 ||
        event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    if (link.getAttribute("aria-busy") === "true") return;
    link.setAttribute("aria-busy", "true");
    var objectUrl;
    try {
      var response = await fetch(link.href, {
        credentials: "same-origin", headers: { "Accept": "text/csv" }
      });
      if (!response.ok || !/^text\/csv(?:;|$)/i.test(response.headers.get("Content-Type") || "")) {
        throw new Error("CSV response unavailable");
      }
      var blob = await response.blob();
      var disposition = response.headers.get("Content-Disposition") || "";
      var name = /filename="([^"]+)"/i.exec(disposition);
      objectUrl = URL.createObjectURL(blob);
      var download = document.createElement("a");
      download.href = objectUrl;
      download.download = name ? name[1] : "playlist.csv";
      download.hidden = true;
      document.body.appendChild(download);
      download.click();
      download.remove();
      notify(link.getAttribute("data-export-success"), false);
    } catch (error) {
      notify("Could not export the playlist. Check your session and try again.", true);
    } finally {
      link.removeAttribute("aria-busy");
      if (objectUrl) window.setTimeout(function () { URL.revokeObjectURL(objectUrl); }, 60000);
    }
  });
}
