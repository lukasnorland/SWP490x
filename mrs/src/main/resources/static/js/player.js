/* ==========================================================================
   Preview bar (4.0): play Song.audioUrl from any row button.
   One global HTML5 audio element. Row controls carry CDN URL + metadata as
   data-* attributes; no backend round-trip to start a track. Next/previous
   walk the list the title was clicked in (catalog JSON queue, or the playlist
   table). State survives a full navigation through sessionStorage.
   ========================================================================== */
"use strict";

import { extractCoverAmbience } from "./color.js";

var IDLE_TITLE = "Nothing playing";
var IDLE_SUBTITLE = "Click a song title to play";
var PLAYER_STATE_KEY = "mrs.previewPlayer";
var PLAY_QUEUE_PATH = "/songs/play-queue";
var REPEAT_OFF = "off";
var REPEAT_ALL = "all";
var REPEAT_ONE = "one";
var PREV_RESTART_SECONDS = 3;

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

function emptyTrack() {
  return {
    id: "",
    url: "",
    title: "",
    artist: "",
    cover: "",
    ambienceA: "",
    ambienceB: "",
    duration: ""
  };
}

function normalizeTrack(raw) {
  if (!raw) {
    return emptyTrack();
  }
  return {
    id: raw.id != null && raw.id !== "" ? String(raw.id) : "",
    url: raw.url || "",
    title: raw.title || "",
    artist: raw.artist || "",
    cover: raw.cover || "",
    ambienceA: raw.ambienceA || "",
    ambienceB: raw.ambienceB || "",
    duration: raw.duration != null ? raw.duration : ""
  };
}

function sameTrack(a, b) {
  if (!a || !b) {
    return false;
  }
  if (a.id && b.id) {
    return String(a.id) === String(b.id);
  }
  return !!(a.url && a.url === b.url);
}

function findTrackIndex(tracks, track) {
  for (var i = 0; i < tracks.length; i++) {
    if (sameTrack(tracks[i], track)) {
      return i;
    }
  }
  return -1;
}

function identityOrder(length) {
  var out = [];
  for (var i = 0; i < length; i++) {
    out.push(i);
  }
  return out;
}

function shuffleKeepingFirst(length, firstIndex) {
  var rest = [];
  for (var i = 0; i < length; i++) {
    if (i !== firstIndex) {
      rest.push(i);
    }
  }
  for (var j = rest.length - 1; j > 0; j--) {
    var k = Math.floor(Math.random() * (j + 1));
    var tmp = rest[j];
    rest[j] = rest[k];
    rest[k] = tmp;
  }
  return [firstIndex].concat(rest);
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
  var shuffleBtn = bar.querySelector("[data-preview-shuffle]");
  var prevBtn = bar.querySelector("[data-preview-prev]");
  var nextBtn = bar.querySelector("[data-preview-next]");
  var repeatBtn = bar.querySelector("[data-preview-repeat]");
  var repeatIconAll = bar.querySelector("[data-preview-icon-repeat]");
  var repeatIconOne = bar.querySelector("[data-preview-icon-repeat-one]");
  if (!audio || !toggle || !progress || !progressBar || !time) {
    return;
  }

  var fallbackDuration = 0;
  var activeTrigger = null;
  var appliedCoverUrl = "";
  var currentCoverUrl = "";
  var currentAmbience = null;
  var currentTrackUrl = "";
  var currentTrack = emptyTrack();
  var ambienceRequestId = 0;
  var persistTimer = null;
  var queue = [];
  var order = [];
  var orderPos = -1;
  var shuffleOn = false;
  var repeatMode = REPEAT_OFF;
  var queueFetchController = null;
  var queueFetchGeneration = 0;

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

  /* The import samples each cover and stores the result on the song, because
     sampling here needs a canvas and a canvas needs CORS headers that several
     vendor CDNs never send. Reading the image is kept as the fallback, for
     songs imported before the colours were stored. */
  function applyShellAmbience(coverUrl, ambience) {
    if (ambience && ambience.a && ambience.b) {
      ambienceRequestId += 1;
      appliedCoverUrl = coverUrl || "";
      document.body.style.setProperty("--shell-ambience-a", ambience.a);
      document.body.style.setProperty("--shell-ambience-b", ambience.b);
      document.body.classList.add("shell--ambience");
      return;
    }
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

  function setArt(coverUrl, ambience) {
    currentCoverUrl = coverUrl || "";
    currentAmbience = ambience && ambience.a && ambience.b ? ambience : null;
    applyShellAmbience(currentCoverUrl, currentAmbience);
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

  function findTriggerFor(track) {
    if (!track || !window.CSS || !CSS.escape) {
      return null;
    }
    if (track.id) {
      var byId = root.querySelector('[data-preview-id="' + CSS.escape(String(track.id)) + '"]');
      if (byId) {
        return byId;
      }
    }
    if (track.url) {
      return root.querySelector('[data-preview-url="' + CSS.escape(track.url) + '"]');
    }
    return null;
  }

  function syncActiveTitleTrigger() {
    if (!currentTrackUrl) {
      markTrigger(null);
      return;
    }
    markTrigger(findTriggerFor(currentTrack.url ? currentTrack : { url: currentTrackUrl }));
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

  function syncRepeatButton() {
    if (!repeatBtn) {
      return;
    }
    var pressed = repeatMode !== REPEAT_OFF;
    repeatBtn.disabled = !currentTrackUrl;
    repeatBtn.setAttribute("aria-pressed", pressed ? "true" : "false");
    var label = "Repeat";
    if (repeatMode === REPEAT_ALL) {
      label = "Repeat all";
    } else if (repeatMode === REPEAT_ONE) {
      label = "Repeat one";
    }
    repeatBtn.setAttribute("aria-label", label);
    if (repeatIconAll) {
      repeatIconAll.classList.toggle("d-none", repeatMode === REPEAT_ONE);
    }
    if (repeatIconOne) {
      repeatIconOne.classList.toggle("d-none", repeatMode !== REPEAT_ONE);
    }
  }

  function syncTransportUi() {
    var loaded = !!currentTrackUrl;
    if (prevBtn) {
      prevBtn.disabled = !loaded;
    }
    if (nextBtn) {
      nextBtn.disabled = !loaded;
    }
    if (shuffleBtn) {
      shuffleBtn.disabled = queue.length < 2;
      shuffleBtn.setAttribute("aria-pressed", shuffleOn ? "true" : "false");
      shuffleBtn.setAttribute("aria-label", shuffleOn ? "Shuffle on" : "Shuffle");
    }
    syncRepeatButton();
  }

  function currentQueueTrack() {
    if (orderPos < 0 || orderPos >= order.length) {
      return currentTrack;
    }
    return queue[order[orderPos]] || currentTrack;
  }

  function rebuildOrder(keepCurrent) {
    var current = keepCurrent || currentQueueTrack() || currentTrack;
    var idx = findTrackIndex(queue, current);
    if (idx < 0) {
      idx = 0;
    }
    if (shuffleOn && queue.length > 1) {
      order = shuffleKeepingFirst(queue.length, idx);
      orderPos = 0;
    } else {
      shuffleOn = shuffleOn && queue.length > 1;
      order = identityOrder(queue.length);
      orderPos = idx;
    }
  }

  function adoptQueue(tracks, current) {
    var next = (tracks || []).filter(function (item) {
      return item && item.url;
    });
    var idx = findTrackIndex(next, current);
    if (idx < 0 && current && current.url) {
      next = [current].concat(next);
      idx = 0;
    }
    queue = next;
    if (queue.length === 0) {
      order = [];
      orderPos = -1;
      syncTransportUi();
      return;
    }
    rebuildOrder(current);
    syncTransportUi();
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
      id: currentTrack.id || "",
      title: title ? title.textContent : "",
      artist: subtitle ? subtitle.textContent : "",
      cover: currentCoverUrl,
      ambienceA: currentAmbience ? currentAmbience.a : "",
      ambienceB: currentAmbience ? currentAmbience.b : "",
      duration: fallbackDuration || knownDuration() || 0,
      currentTime: audio.currentTime || 0,
      playing: !audio.paused && !audio.ended,
      queue: queue,
      order: order,
      orderPos: orderPos,
      shuffle: shuffleOn,
      repeat: repeatMode
    };
    try {
      sessionStorage.setItem(PLAYER_STATE_KEY, JSON.stringify(payload));
    } catch (ignored) {
      try {
        var slim = {
          url: payload.url,
          id: payload.id,
          title: payload.title,
          artist: payload.artist,
          cover: payload.cover,
          ambienceA: payload.ambienceA,
          ambienceB: payload.ambienceB,
          duration: payload.duration,
          currentTime: payload.currentTime,
          playing: payload.playing,
          shuffle: payload.shuffle,
          repeat: payload.repeat
        };
        sessionStorage.setItem(PLAYER_STATE_KEY, JSON.stringify(slim));
      } catch (ignoredQuota) {
        // Ignore quota / privacy errors.
      }
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
    setArt(meta.cover || "", { a: meta.ambienceA, b: meta.ambienceB });
    toggle.disabled = false;
    syncTransportUi();
  }

  function trackFromButton(button) {
    return normalizeTrack({
      id: button.getAttribute("data-preview-id"),
      url: button.getAttribute("data-preview-url"),
      title: button.getAttribute("data-preview-title"),
      artist: button.getAttribute("data-preview-artist"),
      cover: button.getAttribute("data-preview-cover"),
      ambienceA: button.getAttribute("data-preview-ambience-a"),
      ambienceB: button.getAttribute("data-preview-ambience-b"),
      duration: button.getAttribute("data-preview-duration")
    });
  }

  function tracksFromButtons(scope) {
    var tracks = [];
    if (!scope) {
      return tracks;
    }
    scope.querySelectorAll("[data-preview-url]").forEach(function (el) {
      var track = trackFromButton(el);
      if (track.url) {
        tracks.push(track);
      }
    });
    return tracks;
  }

  function catalogQueueUrl(source) {
    var path = PLAY_QUEUE_PATH;
    if (source && source.getAttribute("data-preview-queue-url")) {
      path = source.getAttribute("data-preview-queue-url");
    }
    var params = catalogQueueParams();
    return path + (params ? "?" + params : "");
  }

  function catalogQueueParams() {
    var form = root.querySelector("[data-catalog-filters]");
    if (form) {
      var data = new FormData(form);
      data.delete("page");
      return new URLSearchParams(data).toString();
    }
    var parts = [];
    new URLSearchParams(window.location.search).forEach(function (value, key) {
      if (key !== "page") {
        parts.push(encodeURIComponent(key) + "=" + encodeURIComponent(value));
      }
    });
    return parts.join("&");
  }

  function fetchCatalogQueue(track, source) {
    if (queueFetchController) {
      queueFetchController.abort();
    }
    var generation = ++queueFetchGeneration;
    var controller = new AbortController();
    queueFetchController = controller;
    var url = catalogQueueUrl(source);
    fetch(url, {
      headers: { Accept: "application/json" },
      signal: controller.signal
    }).then(function (response) {
      if (!response.ok) {
        throw new Error("queue " + response.status);
      }
      return response.json();
    }).then(function (body) {
      if (generation !== queueFetchGeneration) {
        return;
      }
      var tracks = Array.isArray(body && body.tracks)
          ? body.tracks.map(normalizeTrack)
          : [];
      if (tracks.length) {
        adoptQueue(tracks, track);
      }
      persistPlayerState();
    }).catch(function (err) {
      if (err && err.name === "AbortError") {
        return;
      }
      persistPlayerState();
    });
  }

  function bindQueueFromSource(button, track) {
    var source = button.closest("[data-preview-queue]");
    var kind = source ? source.getAttribute("data-preview-queue") : "";
    if (kind === "catalog") {
      adoptQueue(tracksFromButtons(source), track);
      fetchCatalogQueue(track, source);
      return;
    }
    if (kind === "list" && source) {
      adoptQueue(tracksFromButtons(source), track);
      persistPlayerState();
      return;
    }
    adoptQueue([track], track);
    persistPlayerState();
  }

  function playFromStart() {
    try {
      audio.currentTime = 0;
    } catch (ignored) {
      // Some browsers throw until metadata is ready.
    }
    setProgress(0);
    syncTimeDisplay();
    return audio.play().then(function () {
      setPlayingUi(true);
      persistPlayerState();
    }).catch(onPlayError);
  }

  function loadTrack(track) {
    if (!track || !track.url) {
      return;
    }
    currentTrack = track;
    currentTrackUrl = track.url;
    applyTrackMeta(track);
    markTrigger(findTriggerFor(track));
    setProgress(0);
    syncTimeDisplay();
    setPlayingUi(false);

    var sameTrackPlaying = audio.getAttribute("src") === track.url;
    if (sameTrackPlaying) {
      playFromStart();
      return;
    }
    audio.src = track.url;
    audio.load();
    audio.play().then(function () {
      setPlayingUi(true);
      persistPlayerState();
    }).catch(onPlayError);
  }

  function stopAtEnd() {
    setPlayingUi(false);
    setProgress(0);
    try {
      audio.currentTime = 0;
    } catch (ignored) {
      // Keep the bar ready even if the element is not seekable yet.
    }
    syncTimeDisplay();
    persistPlayerState();
  }

  function playAdjacent(delta, fromEnded) {
    if (!queue.length || order.length === 0) {
      if (fromEnded) {
        stopAtEnd();
      }
      return;
    }
    var nextPos = orderPos + delta;
    if (nextPos < 0 || nextPos >= order.length) {
      if (repeatMode === REPEAT_ALL && order.length > 0) {
        nextPos = ((nextPos % order.length) + order.length) % order.length;
      } else if (fromEnded) {
        stopAtEnd();
        return;
      } else {
        return;
      }
    }
    orderPos = nextPos;
    loadTrack(queue[order[orderPos]]);
  }

  function loadFromButton(button) {
    var track = trackFromButton(button);
    if (!track.url) {
      return;
    }

    var sameLoaded = currentTrackUrl === track.url && audio.getAttribute("src") === track.url;
    currentTrack = track;
    currentTrackUrl = track.url;
    applyTrackMeta(track);
    markTrigger(button);
    setProgress(0);
    syncTimeDisplay();
    setPlayingUi(false);
    bindQueueFromSource(button, track);

    if (sameLoaded) {
      playFromStart();
      return;
    }

    audio.src = track.url;
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

    currentTrack = normalizeTrack(state);
    currentTrack.url = state.url;
    currentTrackUrl = state.url;
    shuffleOn = !!state.shuffle;
    repeatMode = state.repeat === REPEAT_ALL || state.repeat === REPEAT_ONE
        ? state.repeat
        : REPEAT_OFF;
    if (Array.isArray(state.queue) && state.queue.length) {
      queue = state.queue.map(normalizeTrack).filter(function (item) {
        return !!item.url;
      });
      if (Array.isArray(state.order) && state.order.length) {
        order = state.order.filter(function (index) {
          return index >= 0 && index < queue.length;
        });
      } else {
        order = identityOrder(queue.length);
      }
      orderPos = parseInt(state.orderPos, 10);
      if (!isFinite(orderPos) || orderPos < 0 || orderPos >= order.length) {
        var restoredIndex = findTrackIndex(queue, currentTrack);
        orderPos = restoredIndex < 0 ? 0 : restoredIndex;
        if (!Array.isArray(state.order)) {
          order = identityOrder(queue.length);
        }
      }
    }
    applyTrackMeta(state);
    syncActiveTitleTrigger();
    setProgress(0);
    setPlayingUi(false);
    syncTransportUi();

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

  if (prevBtn) {
    prevBtn.addEventListener("click", function () {
      if (!audio.getAttribute("src")) {
        return;
      }
      if ((audio.currentTime || 0) > PREV_RESTART_SECONDS) {
        playFromStart();
        return;
      }
      playAdjacent(-1, false);
    });
  }

  if (nextBtn) {
    nextBtn.addEventListener("click", function () {
      playAdjacent(1, false);
    });
  }

  if (shuffleBtn) {
    shuffleBtn.addEventListener("click", function () {
      if (queue.length < 2) {
        return;
      }
      shuffleOn = !shuffleOn;
      rebuildOrder(currentQueueTrack() || currentTrack);
      syncTransportUi();
      persistPlayerState();
    });
  }

  if (repeatBtn) {
    repeatBtn.addEventListener("click", function () {
      if (repeatMode === REPEAT_OFF) {
        repeatMode = REPEAT_ALL;
      } else if (repeatMode === REPEAT_ALL) {
        repeatMode = REPEAT_ONE;
      } else {
        repeatMode = REPEAT_OFF;
      }
      syncRepeatButton();
      persistPlayerState();
    });
  }

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
    if (repeatMode === REPEAT_ONE) {
      playFromStart();
      return;
    }
    playAdjacent(1, true);
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
  syncTransportUi();
  restorePersistedState();
}
