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

  /* --- CSRF helpers for JSON API calls --------------------------------- */
  function csrfHeaders() {
    var token = document.querySelector('meta[name="_csrf"]');
    var header = document.querySelector('meta[name="_csrf_header"]');
    var headers = { "Accept": "application/json" };
    if (token && header) {
      headers[header.getAttribute("content")] = token.getAttribute("content");
    }
    return headers;
  }

  function apiJson(url, options) {
    var opts = options || {};
    var headers = Object.assign({}, csrfHeaders(), opts.headers || {});
    if (opts.body && typeof opts.body === "object" && !(opts.body instanceof URLSearchParams)) {
      headers["Content-Type"] = "application/json";
      opts.body = JSON.stringify(opts.body);
    }
    return fetch(url, Object.assign({ credentials: "same-origin" }, opts, { headers: headers }))
      .then(function (response) {
        return response.text().then(function (text) {
          var payload = null;
          if (text) {
            try {
              payload = JSON.parse(text);
            } catch (ignore) {
              payload = { message: text };
            }
          }
          return { ok: response.ok, status: response.status, payload: payload };
        });
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
      var apiRoot = document.querySelector("[data-admin-users-api]");
      if (!input || !status || !form || !apiRoot) {
        return;
      }

      var gated = form.getAttribute("data-ses-gate-create") === "true";
      var submit = form.querySelector("[type='submit']");
      var apiBase = apiRoot.getAttribute("data-admin-users-api");

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

        var label = button.querySelector("span") || button;
        var previous = label.textContent;
        button.disabled = true;
        label.textContent = button.getAttribute("data-busy-label") || "Sending…";
        setCreateEnabled(false);
        showSesStatus(status, "pending", "Contacting Amazon SES…");

        apiJson(apiBase + "/ses-recipients", {
          method: "POST",
          body: { email: email }
        })
          .then(function (result) {
            var verified = result.ok && result.payload && result.payload.status === "already_verified";
            var variant = result.ok ? (verified ? "success" : "info") : "error";
            var message = (result.payload && result.payload.message) || "Unexpected response from the server.";
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

  /* --- P-06a REST CRUD actions ----------------------------------------- */
  function showAdminFlash(variant, message) {
    var host = document.getElementById("adminUsersFlash");
    if (!host) {
      return;
    }
    host.hidden = false;
    host.className = "alert alert-" + variant;
    host.setAttribute("role", "status");
    host.textContent = message;
  }

  function reloadUsersPage() {
    window.location.reload();
  }

  function initAdminUserRest(root) {
    var apiRoot = root.querySelector("[data-admin-users-api]");
    if (!apiRoot) {
      return;
    }
    var apiBase = apiRoot.getAttribute("data-admin-users-api");

    var createForm = document.getElementById("createUserForm");
    if (createForm) {
      createForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var errorHost = document.getElementById("createUserErrors");
        if (errorHost) {
          errorHost.hidden = true;
          errorHost.textContent = "";
        }

        var body = {
          name: document.getElementById("newUserName").value,
          email: document.getElementById("newUserEmail").value,
          role: document.getElementById("newUserRole").value,
          password: document.getElementById("newUserPassword").value
        };

        apiJson(apiBase, { method: "POST", body: body })
          .then(function (result) {
            if (result.ok) {
              var message = (result.payload && result.payload.message)
                  || "User created successfully.";
              sessionStorage.setItem("mrs.adminUsers.flash", JSON.stringify({
                variant: result.payload && result.payload.emailDelivered === false ? "warning" : "success",
                message: message
              }));
              reloadUsersPage();
              return;
            }
            var payload = result.payload || {};
            var message = payload.message || "Could not create the account.";
            if (payload.violations && payload.violations.length) {
              message += " " + payload.violations.join(" ");
            }
            if (errorHost) {
              errorHost.hidden = false;
              errorHost.className = "alert alert-danger";
              errorHost.textContent = message;
            }
          })
          .catch(function () {
            if (errorHost) {
              errorHost.hidden = false;
              errorHost.className = "alert alert-danger";
              errorHost.textContent = "Could not reach the server. Try again.";
            }
          });
      });
    }

    root.querySelectorAll("[data-admin-action]").forEach(function (control) {
      control.addEventListener("click", function () {
        var action = control.getAttribute("data-admin-action");
        var userId = control.getAttribute("data-user-id");
        if (!action || !userId) {
          return;
        }

        var request;
        if (action === "resend") {
          request = apiJson(apiBase + "/" + userId + "/credentials/resend", { method: "POST" });
        } else if (action === "deactivate") {
          if (!window.confirm("Deactivate this account? Open sessions will end on the next request.")) {
            return;
          }
          request = apiJson(apiBase + "/" + userId, { method: "DELETE" });
        } else if (action === "reactivate") {
          request = apiJson(apiBase + "/" + userId, {
            method: "PATCH",
            body: { status: "ACTIVE" }
          });
        } else if (action === "role") {
          request = apiJson(apiBase + "/" + userId, {
            method: "PATCH",
            body: { role: control.getAttribute("data-role") }
          });
        } else {
          return;
        }

        request
          .then(function (result) {
            if (!result.ok) {
              var message = (result.payload && result.payload.message)
                  || "The request could not be completed.";
              showAdminFlash("danger", message);
              return;
            }
            var successMessage = "Updated.";
            if (action === "resend") {
              successMessage = (result.payload && result.payload.message) || successMessage;
            } else if (action === "deactivate") {
              successMessage = "Account deactivated. Any open session will end on the next request.";
            } else if (action === "reactivate") {
              successMessage = "Account reactivated. The user can sign in again.";
            } else if (action === "role") {
              successMessage = "Role updated. The user must sign in again before the new permissions apply.";
            }
            sessionStorage.setItem("mrs.adminUsers.flash", JSON.stringify({
              variant: action === "resend" && result.payload && result.payload.emailDelivered === false
                  ? "warning" : "success",
              message: successMessage
            }));
            reloadUsersPage();
          })
          .catch(function () {
            showAdminFlash("danger", "Could not reach the server. Try again.");
          });
      });
    });

    try {
      var stored = sessionStorage.getItem("mrs.adminUsers.flash");
      if (stored) {
        sessionStorage.removeItem("mrs.adminUsers.flash");
        var flash = JSON.parse(stored);
        showAdminFlash(flash.variant || "success", flash.message || "");
      }
    } catch (ignore) {
      // Ignore corrupt sessionStorage payloads.
    }
  }

  /* --- Auth / password REST forms -------------------------------------- */
  function showInlineError(host, message) {
    if (!host) {
      return;
    }
    host.hidden = false;
    host.className = "alert alert-danger";
    host.setAttribute("role", "alert");
    host.textContent = message;
  }

  function initAuthRestForms(root) {
    var authRoot = root.querySelector("[data-auth-api]");
    var authBase = authRoot ? authRoot.getAttribute("data-auth-api") : null;

    var registerForm = document.getElementById("registerRequestForm");
    if (registerForm && authBase) {
      registerForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var errorHost = document.getElementById("registerRequestError");
        apiJson(authBase + "/register-requests", {
          method: "POST",
          body: { email: document.getElementById("registerEmail").value }
        }).then(function (result) {
          if (result.ok) {
            var flashHost = document.getElementById("landingFlashHost");
            if (flashHost) {
              flashHost.hidden = false;
              flashHost.innerHTML = "";
              var notice = document.createElement("div");
              notice.className = "alert alert-success";
              notice.setAttribute("role", "status");
              notice.textContent = (result.payload && result.payload.message) || "Request sent.";
              flashHost.appendChild(notice);
            }
            if (window.bootstrap) {
              var modal = window.bootstrap.Modal.getInstance(document.getElementById("registerPanel"));
              if (modal) {
                modal.hide();
              }
            }
            registerForm.reset();
            return;
          }
          showInlineError(errorHost,
              (result.payload && result.payload.message) || "Could not send the request.");
        }).catch(function () {
          showInlineError(errorHost, "Could not reach the server. Try again.");
        });
      });
    }

    var resetRequestForm = document.getElementById("passwordResetRequestForm");
    if (resetRequestForm && authBase) {
      resetRequestForm.addEventListener("submit", function (event) {
        event.preventDefault();
        apiJson(authBase + "/password-resets", {
          method: "POST",
          body: { email: document.getElementById("email").value }
        }).then(function () {
          window.location.search = "?sent";
        }).catch(function () {
          window.location.search = "?sent";
        });
      });
    }

    var resetSetForm = document.getElementById("passwordResetSetForm");
    if (resetSetForm && authBase) {
      resetSetForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var errorHost = document.getElementById("passwordResetSetErrors");
        apiJson(authBase + "/password-resets", {
          method: "PUT",
          body: {
            token: document.getElementById("resetToken").value,
            password: document.getElementById("password").value,
            confirmPassword: document.getElementById("confirmPassword").value
          }
        }).then(function (result) {
          if (result.ok) {
            window.location.href = (result.payload && result.payload.redirectTo) || "/login?reset";
            return;
          }
          var message = (result.payload && result.payload.message) || "Could not update the password.";
          if (result.payload && result.payload.violations && result.payload.violations.length) {
            message += " " + result.payload.violations.join(" ");
          }
          showInlineError(errorHost, message);
        }).catch(function () {
          showInlineError(errorHost, "Could not reach the server. Try again.");
        });
      });
    }

    var forcedForm = document.getElementById("forcedPasswordForm");
    var passwordApiRoot = root.querySelector("[data-account-password-api]");
    if (forcedForm && passwordApiRoot) {
      forcedForm.addEventListener("submit", function (event) {
        event.preventDefault();
        var errorHost = document.getElementById("forcedPasswordErrors");
        apiJson(passwordApiRoot.getAttribute("data-account-password-api"), {
          method: "PUT",
          body: {
            currentPassword: document.getElementById("currentPassword").value,
            password: document.getElementById("password").value,
            confirmPassword: document.getElementById("confirmPassword").value
          }
        }).then(function (result) {
          if (result.ok) {
            window.location.href = (result.payload && result.payload.redirectTo) || "/";
            return;
          }
          var message = (result.payload && result.payload.message) || "Could not update the password.";
          if (result.payload && result.payload.violations && result.payload.violations.length) {
            message += " " + result.payload.violations.join(" ");
          }
          showInlineError(errorHost, message);
        }).catch(function () {
          showInlineError(errorHost, "Could not reach the server. Try again.");
        });
      });
    }
  }

  /* --- Submitting state (P-00 "loading": spinner, inputs disabled) ------- */
  function initSubmitStates(root) {
    root.querySelectorAll("form[data-busy-label]").forEach(function (form) {
      // REST-backed forms use fetch; skip the native submit busy path there.
      if (form.id === "createUserForm"
          || form.id === "registerRequestForm"
          || form.id === "passwordResetRequestForm"
          || form.id === "passwordResetSetForm"
          || form.id === "forcedPasswordForm") {
        return;
      }
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
    initAdminUserRest(document);
    initAuthRestForms(document);
    initSubmitStates(document);
  });
})();
