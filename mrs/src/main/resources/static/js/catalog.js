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
  return pathname === "/admin/catalog" || pathname.indexOf("/admin/catalog/") === 0;
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
    var action = form.getAttribute("action") || "/admin/catalog";
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
