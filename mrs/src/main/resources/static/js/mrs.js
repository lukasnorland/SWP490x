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
      window.bootstrap.Modal.getOrCreateInstance(element).show();
    });
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
    initSubmitStates(document);
  });
})();
