/* ==========================================================================
   Preview bar (4.0): play Song.previewUrl from any row button.
   One global HTML5 audio element. Row controls carry CDN URL + metadata as
   data-* attributes; no backend round-trip. State survives a full navigation
   through sessionStorage, so the bar picks up where it left off.
   ========================================================================== */
"use strict";

import { extractCoverAmbience } from "./color.js";

var IDLE_TITLE = "Nothing playing";
var IDLE_SUBTITLE = "Click a song title to play";
var PLAYER_STATE_KEY = "mrs.previewPlayer";

function formatClock(seconds) {
  if (!isFinite(seconds) || seconds < 0) {
    return "0:00";
  }
  var whole = Math.floor(seconds);
  var minutes = Math.floor(whole / 60);
  var remainder = whole % 60;
  return minutes + ":" + (remainder < 10 ? "0" : "") + remainder;
}

function formatElapsedDuration(elapsed, duration) {
  return formatClock(elapsed) + "/" + formatClock(duration);
}

/* Re-mark the row whose title matches what the bar is playing. Content swaps
   (catalog paging, soft-nav) bring in fresh rows that lost the highlight. */
export function syncPlayingTitleHighlight(root) {
  root.querySelectorAll("[data-preview-url][aria-pressed='true']").forEach(function (el) {
    el.removeAttribute("aria-pressed");
  });
  var audio = root.querySelector("[data-preview-audio]");
  if (!audio) {
    return;
  }
  var src = audio.getAttribute("src");
  if (!src || !window.CSS || !CSS.escape) {
    return;
  }
  var match = root.querySelector('[data-preview-url="' + CSS.escape(src) + '"]');
  if (match) {
    match.setAttribute("aria-pressed", "true");
  }
}

export function initPreviewPlayer(root) {
  var bar = root.querySelector("[data-preview-bar]");
  if (!bar) {
    return;
  }

  var audio = bar.querySelector("[data-preview-audio]");
  var art = bar.querySelector("[data-preview-art]");
  var title = bar.querySelector("[data-preview-title]");
  var subtitle = bar.querySelector("[data-preview-subtitle]");
  var toggle = bar.querySelector("[data-preview-toggle]");
  var playIcon = bar.querySelector("[data-preview-icon-play]");
  var pauseIcon = bar.querySelector("[data-preview-icon-pause]");
  var progress = bar.querySelector("[data-preview-progress]");
  var progressBar = bar.querySelector("[data-preview-progress-bar]");
  var time = bar.querySelector("[data-preview-time]");
  if (!audio || !toggle || !progress || !progressBar || !time) {
    return;
  }

  var fallbackDuration = 0;
  var activeTrigger = null;
  var appliedCoverUrl = "";
  var currentCoverUrl = "";
  var currentTrackUrl = "";
  var ambienceRequestId = 0;
  var persistTimer = null;

  function setPlayingUi(playing) {
    if (playIcon) {
      playIcon.classList.toggle("d-none", playing);
    }
    if (pauseIcon) {
      pauseIcon.classList.toggle("d-none", !playing);
    }
    toggle.setAttribute("aria-label", playing ? "Pause" : "Play");
    toggle.setAttribute("aria-pressed", playing ? "true" : "false");
    toggle.removeAttribute("title");
  }

  function setProgress(ratio) {
    var clamped = Math.max(0, Math.min(1, ratio || 0));
    var percent = Math.round(clamped * 100);
    progressBar.style.width = percent + "%";
    progress.setAttribute("aria-valuenow", String(percent));
  }

  function clearShellAmbience() {
    ambienceRequestId += 1;
    appliedCoverUrl = "";
    document.body.classList.remove("shell--ambience");
    document.body.style.removeProperty("--shell-ambience-a");
    document.body.style.removeProperty("--shell-ambience-b");
  }

  function applyShellAmbience(coverUrl) {
    if (!coverUrl) {
      clearShellAmbience();
      return;
    }
    if (coverUrl === appliedCoverUrl && document.body.classList.contains("shell--ambience")) {
      return;
    }

    var requestId = ++ambienceRequestId;
    extractCoverAmbience(coverUrl).then(function (colors) {
      if (requestId !== ambienceRequestId) {
        return;
      }
      appliedCoverUrl = coverUrl;
      document.body.style.setProperty("--shell-ambience-a", colors.a);
      document.body.style.setProperty("--shell-ambience-b", colors.b);
      document.body.classList.add("shell--ambience");
    }).catch(function () {
      if (requestId !== ambienceRequestId) {
        return;
      }
      clearShellAmbience();
    });
  }

  function setArt(coverUrl) {
    currentCoverUrl = coverUrl || "";
    if (!art) {
      return;
    }
    if (coverUrl) {
      art.style.backgroundImage = 'url("' + coverUrl.replace(/"/g, '\\"') + '")';
      art.classList.add("has-cover");
    } else {
      art.style.backgroundImage = "";
      art.classList.remove("has-cover");
    }
    applyShellAmbience(coverUrl || "");
  }

  function markTrigger(button) {
    if (activeTrigger && activeTrigger !== button) {
      activeTrigger.removeAttribute("aria-pressed");
    }
    activeTrigger = button || null;
    if (activeTrigger) {
      activeTrigger.setAttribute("aria-pressed", "true");
    }
  }

  function syncActiveTitleTrigger() {
    if (!currentTrackUrl) {
      markTrigger(null);
      return;
    }
    var match = root.querySelector('[data-preview-url="' + CSS.escape(currentTrackUrl) + '"]');
    markTrigger(match);
  }

  function knownDuration() {
    if (isFinite(audio.duration) && audio.duration > 0) {
      return audio.duration;
    }
    return fallbackDuration;
  }

  function syncTimeDisplay() {
    time.textContent = formatElapsedDuration(audio.currentTime || 0, knownDuration());
  }

  function clearPersistedState() {
    try {
      sessionStorage.removeItem(PLAYER_STATE_KEY);
    } catch (ignored) {
      // Private mode / disabled storage — playback still works in-page.
    }
  }

  function persistPlayerState() {
    if (!currentTrackUrl) {
      clearPersistedState();
      return;
    }
    var payload = {
      url: currentTrackUrl,
      title: title ? title.textContent : "",
      artist: subtitle ? subtitle.textContent : "",
      cover: currentCoverUrl,
      duration: fallbackDuration || knownDuration() || 0,
      currentTime: audio.currentTime || 0,
      playing: !audio.paused && !audio.ended
    };
    try {
      sessionStorage.setItem(PLAYER_STATE_KEY, JSON.stringify(payload));
    } catch (ignored) {
      // Ignore quota / privacy errors.
    }
  }

  function schedulePersist() {
    if (persistTimer) {
      window.clearTimeout(persistTimer);
    }
    persistTimer = window.setTimeout(persistPlayerState, 250);
  }

  function applyTrackMeta(meta) {
    fallbackDuration = parseFloat(meta.duration || "0") || 0;
    if (title) {
      title.textContent = meta.title || "Untitled";
      title.classList.remove("text-secondary");
    }
    if (subtitle) {
      subtitle.textContent = meta.artist || "Unknown artist";
    }
    setArt(meta.cover || "");
    toggle.disabled = false;
  }

  function loadFromButton(button) {
    var url = button.getAttribute("data-preview-url");
    if (!url) {
      return;
    }

    applyTrackMeta({
      title: button.getAttribute("data-preview-title"),
      artist: button.getAttribute("data-preview-artist"),
      cover: button.getAttribute("data-preview-cover"),
      duration: button.getAttribute("data-preview-duration")
    });
    markTrigger(button);
    setProgress(0);
    syncTimeDisplay();
    setPlayingUi(false);

    // Title click always (re)starts from 0. Pause/resume lives only on the bar.
    var sameTrack = currentTrackUrl === url && audio.getAttribute("src") === url;
    if (sameTrack) {
      audio.pause();
      audio.currentTime = 0;
      syncTimeDisplay();
      audio.play().then(function () {
        setPlayingUi(true);
        persistPlayerState();
      }).catch(onPlayError);
      return;
    }

    currentTrackUrl = url;
    audio.src = url;
    audio.load();
    audio.play().then(function () {
      setPlayingUi(true);
      persistPlayerState();
    }).catch(onPlayError);
  }

  function restorePersistedState() {
    var raw;
    try {
      raw = sessionStorage.getItem(PLAYER_STATE_KEY);
    } catch (ignored) {
      return;
    }
    if (!raw) {
      return;
    }

    var state;
    try {
      state = JSON.parse(raw);
    } catch (ignored) {
      clearPersistedState();
      return;
    }
    if (!state || !state.url) {
      clearPersistedState();
      return;
    }

    currentTrackUrl = state.url;
    applyTrackMeta(state);
    syncActiveTitleTrigger();
    setProgress(0);
    setPlayingUi(false);

    var resumeAt = Math.max(0, parseFloat(state.currentTime) || 0);
    var shouldPlay = !!state.playing;

    function seekAndMaybePlay() {
      var duration = knownDuration();
      if (duration > 0 && resumeAt > duration) {
        resumeAt = 0;
      }
      try {
        audio.currentTime = resumeAt;
      } catch (ignored) {
        // Some browsers throw until metadata is ready; loadedmetadata retries.
      }
      syncTimeDisplay();
      if (duration > 0) {
        setProgress(resumeAt / duration);
      }
      if (shouldPlay) {
        audio.play().then(function () {
          setPlayingUi(true);
          persistPlayerState();
        }).catch(function () {
          // Autoplay may be blocked after a full navigation; keep UI ready.
          setPlayingUi(false);
          persistPlayerState();
        });
      } else {
        persistPlayerState();
      }
    }

    audio.src = state.url;
    audio.load();
    if (audio.readyState >= 1) {
      seekAndMaybePlay();
    } else {
      audio.addEventListener("loadedmetadata", seekAndMaybePlay, { once: true });
    }
  }

  function onPlayError() {
    if (subtitle) {
      subtitle.textContent = "Could not play this track";
    }
    setPlayingUi(false);
    persistPlayerState();
  }

  toggle.addEventListener("click", function () {
    if (!audio.getAttribute("src")) {
      return;
    }
    if (audio.paused) {
      audio.play().then(function () {
        setPlayingUi(true);
        persistPlayerState();
      }).catch(onPlayError);
    } else {
      audio.pause();
      setPlayingUi(false);
      persistPlayerState();
    }
  });

  progress.addEventListener("click", function (event) {
    var duration = knownDuration();
    if (!duration) {
      return;
    }
    var rect = progress.getBoundingClientRect();
    if (rect.width <= 0) {
      return;
    }
    var ratio = (event.clientX - rect.left) / rect.width;
    audio.currentTime = Math.max(0, Math.min(1, ratio)) * duration;
    setProgress(ratio);
    syncTimeDisplay();
    persistPlayerState();
  });

  audio.addEventListener("play", function () {
    setPlayingUi(true);
    schedulePersist();
  });
  audio.addEventListener("playing", function () {
    setPlayingUi(true);
    schedulePersist();
  });
  audio.addEventListener("pause", function () {
    setPlayingUi(false);
    schedulePersist();
  });
  audio.addEventListener("ended", function () {
    setPlayingUi(false);
    setProgress(0);
    audio.currentTime = 0;
    syncTimeDisplay();
    // Keep the track loaded in the bar, but mark it stopped for the next page.
    persistPlayerState();
  });
  audio.addEventListener("timeupdate", function () {
    var duration = knownDuration();
    var current = audio.currentTime || 0;
    syncTimeDisplay();
    if (duration > 0) {
      setProgress(current / duration);
    }
    schedulePersist();
  });
  audio.addEventListener("loadedmetadata", syncTimeDisplay);
  audio.addEventListener("durationchange", syncTimeDisplay);
  audio.addEventListener("error", onPlayError);

  // Persist right before a full navigation (pagination, filters, sidebar links).
  window.addEventListener("pagehide", persistPlayerState);
  window.addEventListener("beforeunload", persistPlayerState);

  root.addEventListener("click", function (event) {
    var button = event.target.closest("[data-preview-url]");
    if (!button || button.disabled) {
      return;
    }
    event.preventDefault();
    loadFromButton(button);
  });

  // Idle defaults stay until the first track is chosen.
  if (title && !title.textContent.trim()) {
    title.textContent = IDLE_TITLE;
  }
  if (subtitle && !subtitle.textContent.trim()) {
    subtitle.textContent = IDLE_SUBTITLE;
  }
  setPlayingUi(false);
  syncTimeDisplay();
  restorePersistedState();
}
