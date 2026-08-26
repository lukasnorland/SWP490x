/* ==========================================================================
   P-06b catalog: swap the results table + pager without remounting the player.
   A full navigation would tear down the audio element mid-track, so the pager
   and the filter form fetch a results-only fragment and replace it in place.
   ========================================================================== */
"use strict";

import { syncPlayingTitleHighlight } from "./player.js";

var CATALOG_PARTIAL_VALUE = "results";

/* Bound once initCatalogPartialPaging runs; soft-nav reaches the loader
   through loadCatalogResults so catalog history stays on the partial path. */
var activeLoadResults = null;

export function isCatalogPath(pathname) {
  return pathname === "/admin/catalog" || pathname.indexOf("/admin/catalog/") === 0
      || pathname === "/songs" || pathname.indexOf("/songs/") === 0;
}

export function loadCatalogResults(url, pushUrl) {
  if (!activeLoadResults) {
    return Promise.resolve();
  }
  return activeLoadResults(url, pushUrl);
}

export function initCatalogPartialPaging(root) {
  if (activeLoadResults) {
    return;
  }

  var abortController = null;

  function catalogForm() {
    return root.querySelector("[data-catalog-filters]");
  }

  /* Only swallow further clicks while a swap is in flight. Disabling the
     filter controls greys the whole card for the length of the fetch, which
     reads as a flash on every page step. */
  function setBusy(busy) {
    var current = root.querySelector("[data-catalog-results]");
    if (current) {
      current.setAttribute("aria-busy", busy ? "true" : "false");
      current.querySelectorAll(".pagination .page-link").forEach(function (link) {
        link.style.pointerEvents = busy ? "none" : "";
      });
    }
    var form = catalogForm();
    if (form) {
      form.querySelectorAll("[data-catalog-clear]").forEach(function (link) {
        link.style.pointerEvents = busy ? "none" : "";
      });
    }
  }

  function loadResults(url, pushUrl) {
    if (!root.querySelector("[data-catalog-results]")) {
      return Promise.resolve();
    }
    if (abortController) {
      abortController.abort();
    }
    abortController = new AbortController();
    setBusy(true);

    return fetch(url, {
      method: "GET",
      headers: {
        "Accept": "text/html",
        "X-MRS-Partial": CATALOG_PARTIAL_VALUE
      },
      credentials: "same-origin",
      signal: abortController.signal
    })
      .then(function (response) {
        if (!response.ok) {
          throw new Error("catalog partial failed");
        }
        return response.text();
      })
      .then(function (html) {
        var current = root.querySelector("[data-catalog-results]");
        if (!current) {
          return;
        }
        var doc = new DOMParser().parseFromString(html, "text/html");
        var next = doc.getElementById("catalog-results")
            || doc.querySelector("[data-catalog-results]")
            || doc.body.firstElementChild;
        if (!next) {
          throw new Error("catalog partial missing results root");
        }
        current.replaceWith(document.importNode(next, true));
        if (pushUrl) {
          history.pushState({ mrsCatalogPartial: true }, "", url);
        }
        syncPlayingTitleHighlight(root);
      })
      .catch(function (error) {
        if (error && error.name === "AbortError") {
          return;
        }
        window.location.assign(url);
      })
      .finally(function () {
        setBusy(false);
      });
  }

  activeLoadResults = loadResults;

  function catalogUrlFromForm(form) {
    var action = form.getAttribute("action") || window.location.pathname;
    var params = new URLSearchParams();
    new FormData(form).forEach(function (value, key) {
      if (value !== "") {
        params.append(key, value);
      }
    });
    var query = params.toString();
    return query ? action + (action.indexOf("?") >= 0 ? "&" : "?") + query : action;
  }

  root.addEventListener("click", function (event) {
    var form = catalogForm();
    var clear = event.target.closest("[data-catalog-clear]");
    if (clear && form && form.contains(clear)) {
      event.preventDefault();
      event.stopPropagation();
      loadResults(clear.href, true).then(function () {
        if (!root.querySelector("[data-catalog-results]")) {
          return;
        }
        form.reset();
      });
      return;
    }

    var resultsEl = root.querySelector("[data-catalog-results]");
    if (!resultsEl) {
      return;
    }
    var link = event.target.closest(".pagination .page-link");
    if (!link || !resultsEl.contains(link)) {
      return;
    }
    if (link.getAttribute("aria-disabled") === "true" || link.parentElement.classList.contains("disabled")) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    loadResults(link.href, true);
  });

  root.addEventListener("submit", function (event) {
    var form = event.target.closest("[data-catalog-filters]");
    if (!form || !root.contains(form)) {
      return;
    }
    event.preventDefault();
    event.stopPropagation();
    loadResults(catalogUrlFromForm(form), true);
  });
}

/* UC-29: fill the edit modal from the row that opened it.

   Bound on document (once) so it still works after shell soft-nav swaps
   main.content and after catalog.js replaces the results table. Looking up
   #editSong at click time avoids a stale node from a previous page. */
var songEditBound = false;

export function initCatalogSongEdit(root) {
  if (songEditBound) {
    return;
  }
  songEditBound = true;

  document.addEventListener("click", function (event) {
    var trigger = event.target.closest("[data-edit-song]");
    if (!trigger) {
      return;
    }
    var modalEl = document.getElementById("editSong");
    var form = modalEl && modalEl.querySelector("[data-edit-song-form]");
    if (!form) {
      return;
    }
    fillEditSongForm(form, trigger);
    if (window.bootstrap && window.bootstrap.Modal) {
      window.bootstrap.Modal.getOrCreateInstance(modalEl).show();
    }
  });
}

function fillEditSongForm(form, trigger) {
  var id = trigger.getAttribute("data-song-id");
  form.setAttribute("action", "/admin/catalog/" + id);
  setInputValue(form, "editVersion", trigger.getAttribute("data-song-version") || "0");
  setInputValue(form, "editGenres", trigger.getAttribute("data-song-genres"));
  setInputValue(form, "editMoods", trigger.getAttribute("data-song-moods"));
  setInputValue(form, "editTags", trigger.getAttribute("data-song-tags"));

  var explicit = form.querySelector("#editExplicit");
  if (explicit) {
    explicit.checked = trigger.getAttribute("data-song-explicit") === "true";
  }

  setReadonlyText(form, "[data-edit-readonly='title']",
      trigger.getAttribute("data-song-title"));
  setReadonlyText(form, "[data-edit-readonly='artist']",
      trigger.getAttribute("data-song-artist"));
  setReadonlyText(form, "[data-edit-readonly='duration']",
      formatDuration(trigger.getAttribute("data-song-duration")));
  setReadonlyText(form, "[data-edit-readonly='bpm']",
      trigger.getAttribute("data-song-bpm"));
  setReadonlyText(form, "[data-edit-readonly='isrc']",
      trigger.getAttribute("data-song-isrc"));
  setReadonlyText(form, "[data-edit-readonly='provider']",
      trigger.getAttribute("data-song-provider"));
  setReadonlyText(form, "[data-edit-readonly='externalId']",
      trigger.getAttribute("data-song-external-id"));
  setReadonlyText(form, "[data-edit-readonly='audioUrl']",
      trigger.getAttribute("data-song-audio-url"));
  setReadonlyText(form, "[data-edit-readonly='coverUrl']",
      trigger.getAttribute("data-song-cover-url"));
}

function formatDuration(raw) {
  var seconds = parseInt(raw, 10);
  if (!seconds || seconds < 1) {
    return "";
  }
  var mins = Math.floor(seconds / 60);
  var secs = seconds % 60;
  return mins + ":" + String(secs).padStart(2, "0");
}

function setInputValue(form, id, value) {
  var input = form.querySelector("#" + id);
  if (input) {
    input.value = value == null || value === "null" ? "" : value;
  }
}

function setReadonlyText(form, selector, value) {
  var el = form.querySelector(selector);
  if (!el) {
    return;
  }
  el.textContent = value == null || value === "" || value === "null" ? "—" : value;
}
