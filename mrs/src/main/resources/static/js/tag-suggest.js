/* ==========================================================================
   Typeahead for Genre / Mood / Tags on P-06b. Genres and moods are closed
   lists; tags stay freeform. Without Tom Select the inputs remain plain text.
   ========================================================================== */
"use strict";

function suggestUrlFrom(root) {
  var host = root && root.closest("[data-suggest-url]");
  if (host && host.getAttribute("data-suggest-url")) {
    return host.getAttribute("data-suggest-url");
  }
  var page = document.querySelector("[data-suggest-url]");
  return page ? page.getAttribute("data-suggest-url") : "/admin/catalog/tags/suggest";
}

function splitCsv(value) {
  if (!value) {
    return [];
  }
  return String(value).split(/[,;]/).map(function (part) {
    return part.trim();
  }).filter(Boolean);
}

export function initTagSuggest(root) {
  if (!window.TomSelect) {
    return;
  }
  var scope = root || document;
  scope.querySelectorAll("[data-tag-suggest]").forEach(bindInput);
}

export function setTagSuggestValue(input, csv) {
  if (!input) {
    return;
  }
  var values = splitCsv(csv);
  if (input.tomselect) {
    var ts = input.tomselect;
    ts.clear(true);
    values.forEach(function (value) {
      ts.addOption({ value: value, text: value });
      ts.addItem(value, true);
    });
    return;
  }
  input.value = values.join(", ");
}

function bindInput(input) {
  if (input.tomselect) {
    return;
  }
  var type = input.getAttribute("data-tag-suggest") || "TAGS";
  var create = type === "TAGS";
  var url = suggestUrlFrom(input);
  new window.TomSelect(input, {
    persist: false,
    create: create,
    createOnBlur: create,
    maxItems: null,
    plugins: ["remove_button"],
    delimiter: ",",
    valueField: "value",
    labelField: "text",
    searchField: ["text"],
    load: function (query, callback) {
      var params = new URLSearchParams({
        type: type,
        q: query || "",
        limit: "20"
      });
      fetch(url + "?" + params.toString(), {
        headers: { Accept: "application/json" },
        credentials: "same-origin"
      }).then(function (response) {
        if (!response.ok) {
          callback();
          return;
        }
        return response.json();
      }).then(function (body) {
        var items = body && body.items ? body.items : [];
        callback(items.map(function (name) {
          return { value: name, text: name };
        }));
      }).catch(function () {
        callback();
      });
    },
    loadThrottle: 200
  });
}
