/* ==========================================================================
   P-06c catalog import — progress panel.
   The POST returns as soon as the sync is queued. This polls /admin/import/status
   and fills the modal so a long run is not a frozen page. Without JavaScript
   the forms still submit; the admin reloads to see the last-run summary.
   ========================================================================== */
"use strict";

var POLL_MS = 600;
var pollTimer = null;
var reloading = false;
var seenRunning = false;

export function initImportProgress(root) {
  var scope = root || document;
  var modalEl = scope.querySelector("#importProgress");
  if (!modalEl) {
    stopPolling();
    return;
  }

  var statusUrl = modalEl.getAttribute("data-status-url");
  if (!statusUrl) {
    return;
  }

  seenRunning = modalEl.getAttribute("data-running") === "true";
  reloading = false;

  scope.querySelectorAll("form[data-import-form]").forEach(function (form) {
    if (form.getAttribute("data-import-bound") === "true") {
      return;
    }
    form.setAttribute("data-import-bound", "true");
    form.addEventListener("submit", function () {
      showModal(modalEl);
      startPolling(modalEl, statusUrl);
    });
  });

  if (modalEl.getAttribute("data-running") === "true") {
    showModal(modalEl);
    startPolling(modalEl, statusUrl);
  }
}

function showModal(modalEl) {
  if (!window.bootstrap || !window.bootstrap.Modal) {
    modalEl.classList.add("show");
    modalEl.style.display = "block";
    return;
  }
  window.bootstrap.Modal.getOrCreateInstance(modalEl, {
    backdrop: "static",
    keyboard: false
  }).show();
}

function startPolling(modalEl, statusUrl) {
  stopPolling();
  poll(modalEl, statusUrl);
  pollTimer = window.setInterval(function () {
    poll(modalEl, statusUrl);
  }, POLL_MS);
}

function stopPolling() {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer);
    pollTimer = null;
  }
}

function poll(modalEl, statusUrl) {
  fetch(statusUrl, {
    method: "GET",
    headers: { Accept: "application/json" },
    credentials: "same-origin"
  })
    .then(function (response) {
      if (!response.ok) {
        throw new Error("import status failed");
      }
      return response.json();
    })
    .then(function (progress) {
      render(modalEl, progress);
      if (progress && progress.running) {
        seenRunning = true;
      }
      if (seenRunning && progress && progress.running === false) {
        finish(modalEl, progress);
      }
    })
    .catch(function () {
      var detail = modalEl.querySelector("[data-import-detail]");
      if (detail) {
        detail.textContent = "Could not read import status. Retrying…";
      }
    });
}

function render(modalEl, progress) {
  if (!progress) {
    return;
  }
  var detail = modalEl.querySelector("[data-import-detail]");
  if (detail && progress.detail) {
    detail.textContent = progress.detail;
  }
  var barWrap = modalEl.querySelector(".progress");
  var bar = modalEl.querySelector(".progress-bar");
  var percent = Number(progress.percent) || 0;
  if (barWrap) {
    barWrap.setAttribute("aria-valuenow", String(percent));
  }
  if (bar) {
    bar.style.width = Math.max(4, percent) + "%";
  }
  setText(modalEl, "[data-import-added]", progress.added);
  setText(modalEl, "[data-import-updated]", progress.updated);
  setText(modalEl, "[data-import-skipped]", progress.skipped);
  var processed = modalEl.querySelector("[data-import-processed]");
  if (processed) {
    processed.textContent = (progress.processed || 0) + " / " + (progress.toRead || 0);
  }
}

function setText(modalEl, selector, value) {
  var el = modalEl.querySelector(selector);
  if (el) {
    el.textContent = value == null ? "0" : String(value);
  }
}

function finish(modalEl, progress) {
  if (reloading) {
    return;
  }
  reloading = true;
  stopPolling();
  render(modalEl, progress);
  var detail = modalEl.querySelector("[data-import-detail]");
  if (detail) {
    detail.textContent = progress.detail || "Import finished. Reloading…";
  }
  window.setTimeout(function () {
    window.location.reload();
  }, 400);
}
