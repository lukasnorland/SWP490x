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

function fillDialog(dialog, trigger) {
  var songId = trigger.getAttribute("data-song-id") || "";
  var title = trigger.getAttribute("data-song-title") || "";
  var artist = trigger.getAttribute("data-song-artist");
  var returnTo = window.location.pathname + window.location.search;

  dialog.querySelectorAll("[data-add-song-id]").forEach(function (input) {
    input.value = songId;
  });
  dialog.querySelectorAll("[data-add-return-to]").forEach(function (input) {
    input.value = returnTo;
  });

  var label = dialog.querySelector("[data-add-song-label]");
  if (label) {
    label.textContent = artist && artist !== "null" ? title + " — " + artist : title;
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
