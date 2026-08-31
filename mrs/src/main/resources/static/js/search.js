/* ==========================================================================
   P-02 Search: live 10–200 character counter, and the sticky multi-select bar.
   ========================================================================== */
"use strict";

export function initSearchPrompt(root) {
  var form = (root || document).querySelector("[data-search-prompt]");
  if (!form) {
    return;
  }
  var field = form.querySelector("#searchPrompt");
  var count = form.querySelector("[data-search-count]");
  var submit = form.querySelector("[data-search-submit]");
  if (!field) {
    return;
  }

  function sync() {
    var length = field.value.trim().length;
    if (count) {
      count.textContent = String(field.value.length);
    }
    if (submit) {
      submit.disabled = length < 10 || length > 200;
    }
  }

  field.addEventListener("input", sync);
  sync();
}

export function initSearchSelection(root) {
  var scope = root || document;

  function bar() {
    return scope.querySelector("[data-search-selection-bar]")
        || document.querySelector("[data-search-selection-bar]");
  }

  function sync() {
    var selected = document.querySelectorAll("[data-search-select]:checked");
    var n = selected.length;
    var el = bar();
    if (!el) {
      return;
    }
    el.hidden = n === 0;
    el.querySelectorAll("[data-search-selected-count]").forEach(function (node) {
      node.textContent = String(n);
    });
    var add = el.querySelector("[data-add-selected]");
    if (add) {
      add.disabled = n === 0;
    }
  }

  if (!initSearchSelection.bound) {
    initSearchSelection.bound = true;
    document.addEventListener("change", function (event) {
      if (event.target && event.target.closest("[data-search-select]")) {
        sync();
      }
    });
  }
  sync();
}
