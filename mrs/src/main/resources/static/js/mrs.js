/* ==========================================================================
   MRS progressive enhancement — entry point.
   Every behaviour here is additive: with JavaScript disabled the pages still
   submit and render, since the server is the boundary (design principle 5).

   Each feature lives in its own module so one failure cannot take the others
   down with it, and so the coupling between them is visible as imports:
     forms.js      password toggles, policy checklist, generator, SES, submit
     color.js      cover art -> ambience wash colors
     player.js     preview bar transport, persistence, ambience
     catalog.js    results-only paging that keeps the player mounted; edit-modal fill
     tag-suggest.js      MusicBrainz genre / mood typeahead on catalog forms
     shell-nav.js  sidebar soft-navigation
     import-progress.js  P-06b live catalog sync status panel
     song-upload.js      P-06b Add Song audio + artwork dropzone
     playlist-add.js     FT-06 Add-to-playlist dialog from a Songs row
   ========================================================================== */
"use strict";

import { enhanceForms } from "./forms.js";
import { initPreviewPlayer } from "./player.js";
import { initCatalogPartialPaging, initCatalogSongEdit } from "./catalog.js";
import { initTagSuggest } from "./tag-suggest.js";
import { initShellSoftNav } from "./shell-nav.js";
import { initImportProgress } from "./import-progress.js";
import { initSongUpload } from "./song-upload.js";
import { initPlaylistAdd } from "./playlist-add.js";

function start() {
  enhanceForms(document);
  initPreviewPlayer(document);
  initCatalogPartialPaging(document);
  initCatalogSongEdit(document);
  initTagSuggest(document);
  initShellSoftNav();
  initImportProgress(document);
  initSongUpload(document);
  initPlaylistAdd();
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", start, { once: true });
} else {
  start();
}
