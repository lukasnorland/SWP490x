/* ==========================================================================
   MRS progressive enhancement.
   Every behaviour here is additive: with JavaScript disabled the pages still
   submit and render, since the server is the boundary (design principle 5).
   ========================================================================== */
(function () {
  "use strict";

  /* --- Password show/hide (P-00 Zone B) --------------------------------- */
  function initPasswordToggles(root) {
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

  function initPasswordPolicy(root) {
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

  function initPasswordGenerators(root) {
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
  function initAutoShownModals(root) {
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

  /* --- SES sandbox recipient verification (P-06a) ------------------------
     Calls CreateEmailIdentity through the app so ADMIN does not need the CLI.
     When real SMTP is on, Create account stays disabled until SES reports
     already_verified (server enforces the same rule). */
  function initSesRecipientPreparation(root) {
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

      input.addEventListener("input", function () {
        setCreateEnabled(false);
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
              message += " After they confirm, click Verify for SES again to unlock Create account.";
            }
            showSesStatus(status, variant, message);
            setCreateEnabled(verified);
          })
          .catch(function () {
            showSesStatus(status, "error", "Could not reach the server. Try again.");
            setCreateEnabled(false);
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
  function initSubmitStates(root) {
    root.querySelectorAll("form[data-busy-label]").forEach(function (form) {
      form.addEventListener("submit", function () {
        var button = form.querySelector("[type='submit']");
        if (!button || button.disabled) {
          return;
        }
        button.disabled = true;
        button.innerHTML = "";
        var spinner = document.createElement("span");
        spinner.className = "spinner-border spinner-border-sm";
        spinner.setAttribute("aria-hidden", "true");
        var label = document.createElement("span");
        label.textContent = form.getAttribute("data-busy-label");
        button.append(spinner, label);

        form.querySelectorAll("input").forEach(function (input) {
          input.readOnly = true;
        });
      });
    });
  }

  document.addEventListener("DOMContentLoaded", function () {
    initPasswordToggles(document);
    initPasswordPolicy(document);
    initPasswordGenerators(document);
    initAutoShownModals(document);
    initSesRecipientPreparation(document);
    initSubmitStates(document);
  });
})();
