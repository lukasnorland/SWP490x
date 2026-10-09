/* Shared form enhancements; validation and submission remain server-controlled. */
"use strict";

/* --- Password show/hide (P-00 Zone B) --------------------------------- */
export function initPasswordToggles(root) {
  root.querySelectorAll("[data-password-toggle]").forEach(function (button) {
    var input = document.getElementById(button.getAttribute("data-password-toggle"));
    if (!input) {
      return;
    }
    button.addEventListener("click", function () {
      var hidden = input.type === "password";
      input.type = hidden ? "text" : "password";
      button.setAttribute("aria-pressed", String(hidden));
      button.querySelectorAll("[data-when]").forEach(function (glyph) {
        glyph.hidden = glyph.getAttribute("data-when") !== (hidden ? "visible" : "hidden");
      });
    });
  });
}

/* --- BR-12 live password checklist (P-01, P-05, P-06a) ---------------- */
var PASSWORD_RULES = {
  length: function (value) {
    return value.length >= 8 && value.length <= 64;
  },
  uppercase: function (value) {
    return /[A-Z]/.test(value);
  },
  digit: function (value) {
    return /[0-9]/.test(value);
  },
  special: function (value) {
    return /[^A-Za-z0-9]/.test(value);
  }
};

export function initPasswordPolicy(root) {
  root.querySelectorAll("[data-password-policy]").forEach(function (panel) {
    var input = document.getElementById(panel.getAttribute("data-password-policy"));
    if (!input) {
      return;
    }
    var items = panel.querySelectorAll("[data-rule]");

    function evaluate() {
      items.forEach(function (item) {
        var rule = PASSWORD_RULES[item.getAttribute("data-rule")];
        var met = typeof rule === "function" && rule(input.value);
        item.classList.toggle("text-success-emphasis", met);
      });
    }

    input.addEventListener("input", evaluate);
    evaluate();
  });
}

/* --- Initial password generator (P-06a Zone A) ------------------------
   Mirrors InitialPasswordGenerator, which is the authority: the field is
   already prefilled server-side, so this only offers another one. */
var PASSWORD_ALPHABETS = [
  "ABCDEFGHJKLMNPQRSTUVWXYZ",
  "abcdefghijkmnopqrstuvwxyz",
  "23456789",
  "!@#$%^&*"
];
var GENERATED_LENGTH = 16;

function generatePassword() {
  var everything = PASSWORD_ALPHABETS.join("");
  var characters = PASSWORD_ALPHABETS.map(pick);
  while (characters.length < GENERATED_LENGTH) {
    characters.push(pick(everything));
  }
  for (var i = characters.length - 1; i > 0; i--) {
    var j = randomBelow(i + 1);
    var held = characters[i];
    characters[i] = characters[j];
    characters[j] = held;
  }
  return characters.join("");
}

function pick(alphabet) {
  return alphabet.charAt(randomBelow(alphabet.length));
}

function randomBelow(bound) {
  var values = new Uint32Array(1);
  window.crypto.getRandomValues(values);
  return values[0] % bound;
}

export function initPasswordGenerators(root) {
  root.querySelectorAll("[data-password-generate]").forEach(function (button) {
    var input = document.getElementById(button.getAttribute("data-password-generate"));
    if (!input || !window.crypto) {
      return;
    }
    button.addEventListener("click", function () {
      input.value = generatePassword();
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });
  });
}

/* --- Dialogs a rejected submission has to reopen (P-06a) --------------- */
export function initAutoShownModals(root) {
  if (!window.bootstrap) {
    return;
  }
  root.querySelectorAll("[data-modal-autoshow]").forEach(function (element) {
    if (element.getAttribute("data-modal-autoshow") !== "true") {
      return;
    }
    window.bootstrap.Modal.getOrCreateInstance(element).show();
  });
}

/* --- One dialog for New user and Edit user (P-06a Zone A) --------------
   The button that opens it decides the mode. An auto-shown reopen after a
   rejected submission has no trigger and keeps the server-rendered state. */
export function initUserDialog(root) {
  root.querySelectorAll("[data-user-dialog]").forEach(function (modal) {
    var form = modal.querySelector("form");
    if (!form) {
      return;
    }
    var title = modal.querySelector(".modal-title");
    var submit = form.querySelector("[type='submit']");
    var name = form.elements.namedItem("name");
    var email = form.elements.namedItem("email");
    var role = form.elements.namedItem("role");
    var gated = form.getAttribute("data-ses-gate-create") === "true";

    modal.addEventListener("show.bs.modal", function (event) {
      var trigger = event.relatedTarget;
      if (!trigger) {
        return;
      }
      var editing = trigger.hasAttribute("data-user-edit");

      form.action = editing
          ? trigger.getAttribute("data-user-action")
          : form.getAttribute("data-create-action");
      form.setAttribute("data-busy-label", editing ? "Saving…" : "Creating…");
      setOrRemove(form, "data-edit-user-id", editing ? trigger.getAttribute("data-user-id") : null);
      setOrRemove(form, "data-original-email",
          editing ? trigger.getAttribute("data-user-email") : null);

      name.value = editing ? trigger.getAttribute("data-user-name") : "";
      email.value = editing ? trigger.getAttribute("data-user-email") : "";
      role.value = editing ? trigger.getAttribute("data-user-role") : role.options[0].value;

      title.textContent = editing ? "Edit user" : "New user";
      submit.textContent = editing ? "Save changes" : "Create account";
      submit.disabled = gated && !editing;

      modal.querySelectorAll("[data-create-only]").forEach(function (section) {
        section.hidden = editing;
        section.querySelectorAll("input").forEach(function (field) {
          field.disabled = editing;
        });
      });
      modal.querySelectorAll("[data-edit-only]").forEach(function (section) {
        section.hidden = !editing;
      });
      // Feedback from a previous rejected submission belongs to that attempt.
      modal.querySelectorAll("[data-user-dialog-feedback]").forEach(function (feedback) {
        feedback.remove();
      });
      modal.querySelectorAll("[data-ses-status-target]").forEach(function (button) {
        var status = document.getElementById(button.getAttribute("data-ses-status-target"));
        if (status) {
          status.hidden = true;
          status.textContent = "";
        }
      });
    });
  });
}

function setOrRemove(element, attribute, value) {
  if (value) {
    element.setAttribute(attribute, value);
  } else {
    element.removeAttribute(attribute);
  }
}

/* SES sandbox verification; the server also requires a verified recipient when SMTP is enabled. */
export function initSesRecipientPreparation(root) {
  root.querySelectorAll("[data-ses-prepare-recipient]").forEach(function (button) {
    var input = document.getElementById(button.getAttribute("data-ses-prepare-recipient"));
    var status = document.getElementById(button.getAttribute("data-ses-status-target"));
    var form = button.closest("form");
    if (!input || !status || !form) {
      return;
    }

    var gated = form.getAttribute("data-ses-gate-create") === "true";
    var submit = form.querySelector("[type='submit']");

    function setCreateEnabled(enabled) {
      if (!gated || !submit) {
        return;
      }
      submit.disabled = !enabled;
    }

    // Editing an account: its own address is already verified.
    function keepsOriginalEmail() {
      var original = form.getAttribute("data-original-email");
      return !!original && input.value.trim() === original;
    }

    input.addEventListener("input", function () {
      setCreateEnabled(keepsOriginalEmail());
      status.hidden = true;
      status.textContent = "";
    });

    button.addEventListener("click", function () {
      var email = input.value.trim();
      if (!email) {
        input.focus();
        return;
      }

      var csrf = form.querySelector('input[name="_csrf"]');
      if (!csrf) {
        showSesStatus(status, "error", "Could not start SES verification — reload the page and try again.");
        setCreateEnabled(false);
        return;
      }

      var label = button.querySelector("span") || button;
      var previous = label.textContent;
      button.disabled = true;
      label.textContent = button.getAttribute("data-busy-label") || "Sending…";
      setCreateEnabled(false);
      showSesStatus(status, "pending", "Contacting Amazon SES…");

      var body = new URLSearchParams();
      body.set("email", email);
      body.set("_csrf", csrf.value);
      var editUserId = form.getAttribute("data-edit-user-id");
      if (editUserId) {
        body.set("userId", editUserId);
      }

      fetch("/admin/users/prepare-recipient", {
        method: "POST",
        headers: {
          "Content-Type": "application/x-www-form-urlencoded",
          "Accept": "application/json"
        },
        body: body.toString(),
        credentials: "same-origin"
      })
        .then(function (response) {
          return response.json().then(function (payload) {
            return { ok: response.ok, payload: payload };
          });
        })
        .then(function (result) {
          var verified = result.ok && result.payload.status === "already_verified";
          var variant = result.ok ? (verified ? "success" : "info") : "error";
          var message = result.payload.message || "Unexpected response from the server.";
          if (result.ok && result.payload.status === "sent") {
            message += " After they confirm, click Verify for SES again to unlock "
                + (submit ? submit.textContent.trim() : "Create account") + ".";
          }
          showSesStatus(status, variant, message);
          setCreateEnabled(verified || keepsOriginalEmail());
        })
        .catch(function () {
          showSesStatus(status, "error", "Could not reach the server. Try again.");
          setCreateEnabled(keepsOriginalEmail());
        })
        .finally(function () {
          button.disabled = false;
          label.textContent = previous;
        });
    });
  });
}

function showSesStatus(element, variant, message) {
  element.hidden = false;
  element.textContent = message;
  element.classList.remove("text-success-emphasis", "text-danger-emphasis", "text-secondary");
  if (variant === "success") {
    element.classList.add("text-success-emphasis");
  } else if (variant === "error") {
    element.classList.add("text-danger-emphasis");
  } else {
    element.classList.add("text-secondary");
  }
}

/* --- Submitting state (P-00 "loading": spinner, inputs disabled) ------- */
export function initSubmitStates(root) {
  root.querySelectorAll("form[data-busy-label]").forEach(function (form) {
    form.addEventListener("submit", function (event) {
      if (form.getAttribute("data-busy") === "true") {
        return;
      }
      var button = event.submitter || form.querySelector("[type='submit']");
      if (!button || button.disabled) {
        return;
      }
      form.setAttribute("data-busy", "true");
      form.setAttribute("aria-busy", "true");
      document.body.setAttribute("aria-busy", "true");

      button.disabled = true;
      button.innerHTML = "";
      var spinner = document.createElement("span");
      spinner.className = "spinner-border spinner-border-sm me-2";
      spinner.setAttribute("aria-hidden", "true");
      var label = document.createElement("span");
      label.textContent = button.getAttribute("data-busy-label")
          || form.getAttribute("data-busy-label");
      button.append(spinner, label);

      // The browser builds the POST body right after this handler returns,
      // and disabled fields are left out of it. Deferring the lock keeps
      // every value (notably <select>s such as Role) in the submission.
      var fields = form.querySelectorAll("input, textarea, select");
      window.setTimeout(function () {
        fields.forEach(function (field) {
          if (field === button || field.type === "submit" || field.type === "hidden") {
            return;
          }
          if (field.type === "checkbox" || field.type === "radio" || field.tagName === "SELECT") {
            field.disabled = true;
            return;
          }
          field.readOnly = true;
        });
      }, 0);

      form.querySelectorAll("[data-busy-status]").forEach(function (status) {
        status.hidden = false;
      });
    });
  });
}

var confirmListenerBound = false;

export function initConfirmSubmits() {
  if (confirmListenerBound) {
    return;
  }
  confirmListenerBound = true;
  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!form || form.tagName !== "FORM") {
      return;
    }
    var submitter = event.submitter;
    var message = (submitter && submitter.getAttribute("data-confirm"))
        || form.getAttribute("data-confirm");
    if (!message) {
      return;
    }
    if (!window.confirm(message)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }, true);
}

/* Re-run every form enhancement over a scope. Soft-nav calls this on the
   swapped-in content, so anything added here is picked up there too. */
export function enhanceForms(scope) {
  initPasswordToggles(scope);
  initPasswordPolicy(scope);
  initPasswordGenerators(scope);
  initUserDialog(scope);
  initAutoShownModals(scope);
  initSesRecipientPreparation(scope);
  initConfirmSubmits();
  initSubmitStates(scope);
}
