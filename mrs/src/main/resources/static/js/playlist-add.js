/* ==========================================================================
   FT-06: open the Add-to-playlist dialog from a row of the Songs table.

   The dialog is a plain pair of POST forms, so the server stays the boundary
   (design principle 5). All this does is say which song the click meant, and
   point the "add to existing" form at the playlist the select is showing.
   ========================================================================== */
"use strict";

var bound = false;

export function initPlaylistAdd() {
  if (bound) {
    return;
  }
  bound = true;

  /* Bound on document, like initCatalogSongEdit: the + buttons live inside
     #catalog-results, which catalog.js replaces on every filter and pager
     step, and shell-nav swaps main.content underneath both. Looking the
     dialog up at click time avoids holding a node from a previous page. */
  document.addEventListener("click", function (event) {
    var trigger = event.target.closest("[data-add-to-playlist]");
    if (!trigger) {
      return;
    }
    var dialog = document.getElementById("addToPlaylist");
    if (!dialog) {
      return;
    }
    fillDialog(dialog, trigger);
    if (window.bootstrap && window.bootstrap.Modal) {
      window.bootstrap.Modal.getOrCreateInstance(dialog).show();
    }
  });

  /* "Create playlist from results": same dialog, only the naming form, and
     the current Search filters copied from the URL so all pages are covered. */
  document.addEventListener("click", function (event) {
    var trigger = event.target.closest("[data-create-from-results]");
    if (!trigger) {
      return;
    }
    var dialog = document.getElementById("addToPlaylist");
    if (!dialog) {
      return;
    }
    fillFromResultsDialog(dialog, trigger);
    if (window.bootstrap && window.bootstrap.Modal) {
      window.bootstrap.Modal.getOrCreateInstance(dialog).show();
    }
  });

  document.addEventListener("change", function (event) {
    var select = event.target.closest("[data-add-playlist-select]");
    if (select) {
      pointAtSelectedPlaylist(select);
    }
  });

  /* Belt and braces: if the change listener never ran — the user submitted
     without touching the select — the action still names the right playlist. */
  document.addEventListener("submit", function (event) {
    var form = event.target.closest("[data-add-existing-form]");
    if (!form) {
      return;
    }
    var select = form.querySelector("[data-add-playlist-select]");
    if (select) {
      pointAtSelectedPlaylist(select);
    }
  });

  document.addEventListener("click", function (event) {
    var trigger = event.target.closest("[data-rename-playlist]");
    if (!trigger) {
      return;
    }
    var dialog = document.getElementById("renamePlaylist");
    if (!dialog) {
      return;
    }
    fillRenameDialog(dialog, trigger);
    if (window.bootstrap && window.bootstrap.Modal) {
      window.bootstrap.Modal.getOrCreateInstance(dialog).show();
    }
  });
}

var SEARCH_CRITERIA = ["genreId", "moodId", "artistId", "tagId", "q", "topN"];

function setMode(dialog, fromResults) {
  dialog.querySelectorAll("[data-add-song-mode]").forEach(function (node) {
    node.hidden = fromResults;
  });
  var results = dialog.querySelector("[data-create-from-results-form]");
  if (results) {
    results.hidden = !fromResults;
    var name = results.querySelector("#createFromResultsName");
    if (name) {
      name.required = fromResults;
    }
  }
  var title = dialog.querySelector("[data-add-title]");
  if (title) {
    title.textContent = fromResults ? "Create playlist from results" : "Add to playlist";
  }
}

function fillFromResultsDialog(dialog, trigger) {
  setMode(dialog, true);
  var returnTo = window.location.pathname + window.location.search;
  dialog.querySelectorAll("[data-add-return-to]").forEach(function (input) {
    input.value = returnTo;
  });

  var holder = dialog.querySelector("[data-create-from-results-criteria]");
  if (holder) {
    holder.textContent = "";
    var params = new URLSearchParams(window.location.search);
    SEARCH_CRITERIA.forEach(function (key) {
      params.getAll(key).forEach(function (value) {
        if (value === "") {
          return;
        }
        var input = document.createElement("input");
        input.type = "hidden";
        input.name = key;
        input.value = value;
        holder.appendChild(input);
      });
    });
  }

  var count = parseInt(trigger.getAttribute("data-result-count") || "0", 10);
  var label = dialog.querySelector("[data-add-song-label]");
  if (label) {
    label.textContent = count === 1
        ? "The 1 matching song will be added, in ranked order."
        : "All " + count + " matching songs will be added, in ranked order.";
  }

  var name = dialog.querySelector("#createFromResultsName");
  if (name) {
    name.value = "";
  }
}

function fillDialog(dialog, trigger) {
  setMode(dialog, false);
  var ids = selectedSongIds(trigger);
  var title = trigger.getAttribute("data-song-title") || "";
  var artist = trigger.getAttribute("data-song-artist");
  var returnTo = window.location.pathname + window.location.search;

  dialog.querySelectorAll("[data-add-song-ids]").forEach(function (holder) {
    fillSongIds(holder, ids);
  });
  dialog.querySelectorAll("[data-add-return-to]").forEach(function (input) {
    input.value = returnTo;
  });

  var label = dialog.querySelector("[data-add-song-label]");
  if (label) {
    if (ids.length > 1) {
      label.textContent = ids.length + " songs selected.";
    } else {
      label.textContent = artist && artist !== "null" ? title + " — " + artist : title;
    }
  }

  var select = dialog.querySelector("[data-add-playlist-select]");
  if (select) {
    pointAtSelectedPlaylist(select);
  }

  var name = dialog.querySelector("#addToPlaylistName");
  if (name) {
    name.value = "";
  }
}

function selectedSongIds(trigger) {
  if (trigger.hasAttribute("data-add-selected")) {
    var ids = [];
    document.querySelectorAll("[data-search-select]:checked").forEach(function (box) {
      if (box.value) {
        ids.push(box.value);
      }
    });
    return ids;
  }
  var one = trigger.getAttribute("data-song-id") || "";
  return one ? [one] : [];
}

function fillSongIds(holder, ids) {
  var template = holder.querySelector("[data-add-song-id]");
  if (!template) {
    return;
  }
  holder.querySelectorAll("[data-add-song-id]").forEach(function (input, index) {
    if (index > 0) {
      input.remove();
    }
  });
  var first = holder.querySelector("[data-add-song-id]");
  if (!ids.length) {
    first.value = "";
    return;
  }
  first.value = ids[0];
  for (var i = 1; i < ids.length; i++) {
    var extra = first.cloneNode(true);
    extra.value = ids[i];
    holder.appendChild(extra);
  }
}

function pointAtSelectedPlaylist(select) {
  var form = select.closest("[data-add-existing-form]");
  if (!form || !select.value) {
    return;
  }
  form.setAttribute("action", "/playlists/" + encodeURIComponent(select.value) + "/songs");
}

function fillRenameDialog(dialog, trigger) {
  var id = trigger.getAttribute("data-playlist-id") || "";
  var name = trigger.getAttribute("data-playlist-name") || "";
  var form = dialog.querySelector("[data-rename-form]");
  if (form && id) {
    form.setAttribute("action", "/playlists/" + encodeURIComponent(id) + "/rename");
  }
  var input = dialog.querySelector("[data-rename-name]");
  if (input) {
    input.value = name;
  }
  var returnTo = dialog.querySelector("[data-rename-return-to]");
  if (returnTo) {
    returnTo.value = window.location.pathname + window.location.search;
  }
}
