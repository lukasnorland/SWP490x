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
    initSubmitStates(document);
  });
})();
