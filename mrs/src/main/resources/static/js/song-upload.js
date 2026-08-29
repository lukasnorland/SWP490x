/* ==========================================================================
   P-06b Add Song — audio & artwork upload.
   Drop audio files to spawn one metadata section each. Duration is read from
   the file in the browser. The form still posts multipart; XHR is used only
   so a large batch can show upload progress.
   ========================================================================== */
"use strict";

import { initTagSuggest } from "./tag-suggest.js";

var AUDIO_EXTENSIONS = /\.(mp3|wav|flac|m4a|mp4|ogg|aac)$/i;
var COVER_EXTENSIONS = /\.(jpe?g|png|webp)$/i;
var MAX_DRAFTS = 10;
var MAX_AUDIO_BYTES = 100 * 1024 * 1024;
var MAX_COVER_BYTES = 5 * 1024 * 1024;

export function initSongUpload(root) {
  var scope = root || document;
  var panel = scope.querySelector("[data-song-upload]");
  if (!panel) {
    return;
  }

  var template = scope.querySelector("#songDraftTemplate");
  var form = panel.querySelector("[data-song-upload-form]");
  var list = panel.querySelector("[data-song-drafts]");
  var dropzone = panel.querySelector("[data-audio-dropzone]");
  var picker = panel.querySelector("[data-audio-picker]");
  var submit = panel.querySelector("[data-song-upload-submit]");
  var progressWrap = panel.querySelector("[data-upload-progress]");
  var progressLabel = panel.querySelector("[data-upload-progress-label]");
  var errorEl = panel.querySelector("[data-upload-error]");
  if (!template || !form || !list || !dropzone) {
    return;
  }

  bindDropzone(dropzone, picker, function (files) {
    addAudioFiles(files);
  });

  if (picker) {
    picker.addEventListener("change", function () {
      addAudioFiles(picker.files);
      picker.value = "";
    });
  }

  form.addEventListener("submit", function (event) {
    event.preventDefault();
    submitDrafts();
  });

  function addAudioFiles(fileList) {
    var files = Array.prototype.slice.call(fileList || []).filter(isAudioFile);
    if (!files.length) {
      return;
    }
    var oversized = [];
    files = files.filter(function (file) {
      if (file.size > MAX_AUDIO_BYTES) {
        oversized.push(file.name);
        return false;
      }
      return true;
    });
    if (oversized.length) {
      showError(oversized.join(", ") + " is larger than 100 MB.");
      if (!files.length) {
        return;
      }
    }
    var remaining = MAX_DRAFTS - list.querySelectorAll("[data-song-draft]").length;
    if (remaining <= 0) {
      showError("At most " + MAX_DRAFTS + " songs per upload.");
      return;
    }
    files.slice(0, remaining).forEach(addDraft);
    if (files.length > remaining) {
      showError("Only the first " + remaining + " file(s) were added (max " + MAX_DRAFTS + ").");
    } else if (!oversized.length) {
      clearError();
    }
    reindex();
    updateSubmit();
  }

  function addDraft(file) {
    var fragment = template.content.cloneNode(true);
    var article = fragment.querySelector("[data-song-draft]");
    list.appendChild(fragment);
    article = list.lastElementChild;

    var heading = article.querySelector("[data-draft-heading]");
    var filename = article.querySelector("[data-draft-filename]");
    var titleInput = article.querySelector("[data-field='title']");
    if (heading) {
      heading.textContent = stemOf(file.name) || "New song";
    }
    if (filename) {
      filename.textContent = file.name;
    }
    if (titleInput && !titleInput.value) {
      titleInput.value = stemOf(file.name);
    }

    assignFile(article.querySelector("[data-field='audio']"), file);
    readDuration(file, article.querySelector("[data-field='duration']"));
    initTagSuggest(article);

    article.querySelector("[data-remove-draft]").addEventListener("click", function () {
      article.remove();
      reindex();
      updateSubmit();
    });

    var coverZone = article.querySelector("[data-cover-dropzone]");
    var coverInput = article.querySelector("[data-field='cover']");
    bindDropzone(coverZone, coverInput, function (files) {
      var cover = Array.prototype.slice.call(files || []).find(isCoverFile);
      if (!cover) {
        return;
      }
      if (cover.size > MAX_COVER_BYTES) {
        showError(cover.name + " is larger than 5 MB.");
        return;
      }
      assignFile(coverInput, cover);
      var preview = article.querySelector("[data-cover-preview]");
      if (preview) {
        if (preview.dataset.objectUrl) {
          URL.revokeObjectURL(preview.dataset.objectUrl);
        }
        var url = URL.createObjectURL(cover);
        preview.dataset.objectUrl = url;
        preview.src = url;
        preview.classList.remove("d-none");
      }
    });
  }

  function reindex() {
    list.querySelectorAll("[data-song-draft]").forEach(function (article, index) {
      article.querySelectorAll("[name]").forEach(function (input) {
        input.name = input.name.replace(/drafts\[\d+\]|drafts\[__i__\]/, "drafts[" + index + "]");
      });
    });
  }

  function updateSubmit() {
    if (!submit) {
      return;
    }
    var empty = list.querySelectorAll("[data-song-draft]").length === 0;
    submit.disabled = empty || submit.getAttribute("data-busy") === "true";
  }

  function submitDrafts() {
    clearError();
    reindex();
    if (list.querySelectorAll("[data-song-draft]").length === 0) {
      showError("Drop one or more audio files first.");
      return;
    }

    var xhr = new XMLHttpRequest();
    xhr.open("POST", panel.getAttribute("data-upload-url") || form.getAttribute("action"));
    xhr.setRequestHeader("X-Requested-With", "XMLHttpRequest");
    xhr.setRequestHeader("Accept", "application/json");
    // CSRF must be a header. CsrfFilter runs before the multipart body is
    // parsed, so a token that only lives in FormData is invisible. Tomcat
    // then cannot drain a song larger than 2 MB and Chrome shows
    // net::ERR_CONNECTION_RESET instead of 403.
    var csrf = form.querySelector("input[name='_csrf']");
    if (csrf && csrf.value) {
      xhr.setRequestHeader("X-CSRF-TOKEN", csrf.value);
    }

    xhr.upload.addEventListener("progress", function (event) {
      if (!event.lengthComputable) {
        return;
      }
      var percent = Math.max(4, Math.round((event.loaded / event.total) * 100));
      showProgress(percent, "Uploading " + percent + "%…");
    });

    xhr.addEventListener("load", function () {
      setBusy(false);
      var payload = parseJson(xhr.responseText);
      if (xhr.status >= 200 && xhr.status < 300) {
        var redirect = payload && payload.redirect ? payload.redirect : form.getAttribute("action");
        window.location.assign(redirect);
        return;
      }
      var message = (payload && payload.message) || (xhr.status === 413
          ? "A file is larger than 100 MB. Compress it or split the batch and try again."
          : "The songs could not be uploaded.");
      if (payload && payload.rejected && payload.rejected.length) {
        message += " " + payload.rejected.map(function (row) {
          return (row.key || "song") + ": " + (row.reason || "");
        }).join(" ");
      }
      showError(message);
      hideProgress();
    });

    xhr.addEventListener("error", function () {
      setBusy(false);
      hideProgress();
      showError("The upload could not be completed. Check the connection and try again.");
    });

    setBusy(true);
    showProgress(4, "Uploading…");
    xhr.send(new FormData(form));
  }

  function setBusy(busy) {
    if (submit) {
      submit.setAttribute("data-busy", busy ? "true" : "false");
    }
    updateSubmit();
  }

  function showProgress(percent, label) {
    if (!progressWrap) {
      return;
    }
    progressWrap.classList.remove("d-none");
    if (progressLabel && label) {
      progressLabel.textContent = label;
    }
    var barWrap = progressWrap.querySelector(".progress");
    var bar = progressWrap.querySelector(".progress-bar");
    if (barWrap) {
      barWrap.setAttribute("aria-valuenow", String(percent));
    }
    if (bar) {
      bar.style.width = percent + "%";
    }
  }

  function hideProgress() {
    if (progressWrap) {
      progressWrap.classList.add("d-none");
    }
  }

  function showError(message) {
    if (!errorEl) {
      return;
    }
    errorEl.textContent = message;
    errorEl.classList.remove("d-none");
  }

  function clearError() {
    if (!errorEl) {
      return;
    }
    errorEl.textContent = "";
    errorEl.classList.add("d-none");
  }
}

function bindDropzone(zone, input, onFiles) {
  if (!zone) {
    return;
  }
  zone.addEventListener("click", function (event) {
    if (event.target === input) {
      return;
    }
    if (input) {
      input.click();
    }
  });
  zone.addEventListener("keydown", function (event) {
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      if (input) {
        input.click();
      }
    }
  });
  ["dragenter", "dragover"].forEach(function (name) {
    zone.addEventListener(name, function (event) {
      event.preventDefault();
      zone.classList.add("is-dragover");
    });
  });
  ["dragleave", "drop"].forEach(function (name) {
    zone.addEventListener(name, function () {
      zone.classList.remove("is-dragover");
    });
  });
  zone.addEventListener("drop", function (event) {
    event.preventDefault();
    if (event.dataTransfer && event.dataTransfer.files) {
      onFiles(event.dataTransfer.files);
    }
  });
  if (input && input !== zone.querySelector("[data-audio-picker]")) {
    input.addEventListener("change", function () {
      onFiles(input.files);
    });
  }
}

function assignFile(input, file) {
  if (!input || !file) {
    return;
  }
  var transfer = new DataTransfer();
  transfer.items.add(file);
  input.files = transfer.files;
}

function readDuration(file, input) {
  if (!file || !input) {
    return;
  }
  var url = URL.createObjectURL(file);
  var audio = document.createElement("audio");
  audio.preload = "metadata";
  audio.addEventListener("loadedmetadata", function () {
    if (Number.isFinite(audio.duration) && audio.duration > 0) {
      input.value = String(Math.round(audio.duration));
    }
    URL.revokeObjectURL(url);
  });
  audio.addEventListener("error", function () {
    URL.revokeObjectURL(url);
  });
  audio.src = url;
}

function isAudioFile(file) {
  if (!file) {
    return false;
  }
  if (file.type && file.type.indexOf("audio/") === 0) {
    return true;
  }
  return AUDIO_EXTENSIONS.test(file.name || "");
}

function isCoverFile(file) {
  if (!file) {
    return false;
  }
  if (file.type && file.type.indexOf("image/") === 0) {
    return true;
  }
  return COVER_EXTENSIONS.test(file.name || "");
}

function stemOf(name) {
  if (!name) {
    return "";
  }
  return name.replace(/\.[^.]+$/, "").replace(/[_-]+/g, " ").trim();
}

function parseJson(text) {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch (e) {
    return null;
  }
}
