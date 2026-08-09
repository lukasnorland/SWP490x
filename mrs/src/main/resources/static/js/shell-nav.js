/* ==========================================================================
   Soft-nav: sidebar only — swap main.content, keep the player mounted.
   Catalog Prev/Next/Filter stay on the lighter results-only partial path.
   Soft-nav must not listen on .shell__main or its popstate will restomp
   catalog history with a full main swap.
   ========================================================================== */
"use strict";

import { enhanceForms } from "./forms.js";
import { syncPlayingTitleHighlight } from "./player.js";
import { isCatalogPath, loadCatalogResults } from "./catalog.js";

var bound = false;

function updateSidebarActive(pathname) {
  document.querySelectorAll(".sidebar .nav-link").forEach(function (link) {
    var href = link.getAttribute("href");
    if (!href || href === "#") {
      return;
    }
    var active = false;
    try {
      var linkPath = new URL(href, window.location.origin).pathname;
      active = linkPath === pathname;
    } catch (ignored) {
      active = false;
    }
    link.classList.toggle("active", active);
    if (active) {
      link.setAttribute("aria-current", "page");
    } else {
      link.removeAttribute("aria-current");
    }
  });
}

function replaceTopbar(fromDoc) {
  var currentNav = document.querySelector(".topbar nav[aria-label='Breadcrumb']");
  var nextNav = fromDoc.querySelector(".topbar nav[aria-label='Breadcrumb']");
  if (currentNav && nextNav) {
    currentNav.replaceWith(document.importNode(nextNav, true));
  }
}

function closeMobileSidebar() {
  var sidebar = document.getElementById("sidebar");
  if (!sidebar || !window.bootstrap) {
    return;
  }
  var instance = window.bootstrap.Offcanvas.getInstance(sidebar);
  if (instance) {
    instance.hide();
  }
}

function shouldSoftNavigate(link, event) {
  if (!link || event.defaultPrevented) {
    return false;
  }
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return false;
  }
  if (link.target && link.target !== "_self") {
    return false;
  }
  if (link.hasAttribute("download")) {
    return false;
  }
  if (link.closest("form[action*='logout']")) {
    return false;
  }
  // Sidebar only — never steal catalog pager/filter/content links.
  if (!link.closest(".sidebar")) {
    return false;
  }
  var href = link.getAttribute("href");
  if (!href || href.charAt(0) === "#") {
    return false;
  }
  var url;
  try {
    url = new URL(href, window.location.origin);
  } catch (ignored) {
    return false;
  }
  if (url.origin !== window.location.origin) {
    return false;
  }
  if (url.pathname.indexOf("/login") === 0 || url.pathname.indexOf("/logout") === 0) {
    return false;
  }
  if (url.pathname === window.location.pathname && url.search === window.location.search) {
    return false;
  }
  return true;
}

export function initShellSoftNav() {
  if (!document.body.classList.contains("shell")) {
    return;
  }
  if (bound) {
    return;
  }
  bound = true;

  var abortController = null;

  function loadShellPage(url, pushUrl) {
    if (abortController) {
      abortController.abort();
    }
    abortController = new AbortController();
    document.body.setAttribute("aria-busy", "true");

    return fetch(url, {
      method: "GET",
      headers: { "Accept": "text/html" },
      credentials: "same-origin",
      signal: abortController.signal
    })
      .then(function (response) {
        if (!response.ok) {
          throw new Error("shell soft-nav failed");
        }
        // Login redirect or auth loss — hard navigate.
        var finalUrl = response.url || url;
        if (finalUrl.indexOf("/login") >= 0) {
          window.location.assign(finalUrl);
          return null;
        }
        return response.text().then(function (html) {
          return { html: html, url: finalUrl };
        });
      })
      .then(function (payload) {
        if (!payload) {
          return;
        }
        var doc = new DOMParser().parseFromString(payload.html, "text/html");
        if (!doc.body.classList.contains("shell")) {
          window.location.assign(payload.url);
          return;
        }
        var nextMain = doc.querySelector("main.content");
        var currentMain = document.querySelector("main.content");
        if (!nextMain || !currentMain) {
          window.location.assign(payload.url);
          return;
        }

        currentMain.innerHTML = nextMain.innerHTML;
        document.title = doc.title || document.title;
        replaceTopbar(doc);
        updateSidebarActive(new URL(payload.url, window.location.origin).pathname);
        closeMobileSidebar();
        enhanceForms(currentMain);
        syncPlayingTitleHighlight(document);

        if (pushUrl) {
          history.pushState({ mrsShellNav: true }, "", payload.url);
        }
        window.scrollTo(0, 0);
      })
      .catch(function (error) {
        if (error && error.name === "AbortError") {
          return;
        }
        window.location.assign(url);
      })
      .finally(function () {
        document.body.removeAttribute("aria-busy");
      });
  }

  document.addEventListener("click", function (event) {
    var link = event.target.closest("a[href]");
    if (!shouldSoftNavigate(link, event)) {
      return;
    }
    event.preventDefault();
    loadShellPage(link.href, true);
  });

  window.addEventListener("popstate", function (event) {
    if (!document.body.classList.contains("shell")) {
      return;
    }
    var path = window.location.pathname;
    var state = event.state || {};
    // Catalog Prev/Next history must stay on the results-only partial path.
    if (state.mrsCatalogPartial
        || (isCatalogPath(path) && document.querySelector("[data-catalog-results]"))) {
      loadCatalogResults(window.location.href, false);
      return;
    }
    loadShellPage(window.location.href, false);
  });
}
