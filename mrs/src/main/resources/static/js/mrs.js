/* Initializes independent form, catalog, search and playback enhancements. */
"use strict";

import { enhanceForms } from "./forms.js";
import { initPreviewPlayer } from "./player.js";
import { initCatalogPartialPaging, initCatalogSongEdit } from "./catalog.js";
import { initTagSuggest } from "./tag-suggest.js";
import { initShellSoftNav } from "./shell-nav.js";
import { initImportProgress } from "./import-progress.js";
import { initSongUpload } from "./song-upload.js";
import { initPlaylistAdd } from "./playlist-add.js";
import { initPlaylistExport } from "./playlist-export.js";
import { initSearchPrompt, initSearchSelection } from "./search.js";

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
  initPlaylistExport();
  initSearchPrompt(document);
  initSearchSelection(document);
}

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", start, { once: true });
} else {
  start();
}
