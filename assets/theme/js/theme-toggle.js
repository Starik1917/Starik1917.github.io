(function () {
  "use strict";

  var STORAGE_KEY = "swordland-theme";
  var THEMES = ["dark", "light"];

  function getFilename() {
    var path = window.location.pathname || "";
    var cleanPath = path.split("?")[0].split("#")[0];
    var parts = cleanPath.split("/").filter(Boolean);
    return (parts[parts.length - 1] || "index.html").toLowerCase();
  }

  function isPlayerPage() {
    return /^player_/.test(getFilename());
  }

  function getDefaultTheme(playerPage) {
    return playerPage ? "light" : "dark";
  }

  function getStoredTheme() {
    try {
      var value = window.localStorage.getItem(STORAGE_KEY);
      return THEMES.indexOf(value) !== -1 ? value : null;
    } catch (error) {
      return null;
    }
  }

  function storeTheme(theme) {
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch (error) {
      // Ignore storage errors in private browsing or locked environments.
    }
  }

  function normalizeTheme(theme, fallback) {
    return THEMES.indexOf(theme) !== -1 ? theme : fallback;
  }

  function applyTheme(theme) {
    var html = document.documentElement;
    var body = document.body;
    html.setAttribute("data-theme", theme);
    body.setAttribute("data-theme", theme);
  }

  function updateButtons(theme) {
    var buttons = document.querySelectorAll(".theme-switcher-btn");
    buttons.forEach(function (button) {
      var isActive = button.getAttribute("data-theme-option") === theme;
      button.classList.toggle("active", isActive);
      button.setAttribute("aria-pressed", isActive ? "true" : "false");
    });
  }

  function buildSwitcher(currentTheme, onSelect) {
    var container = document.createElement("div");
    container.className = "theme-switcher";
    container.setAttribute("role", "group");
    container.setAttribute("aria-label", "\u0412\u044b\u0431\u043e\u0440 \u0442\u0435\u043c\u044b");

    container.innerHTML =
      '<span class="theme-switcher-title">\u0422\u0435\u043c\u0430</span>' +
      '<button class="theme-switcher-btn" type="button" data-theme-option="dark" aria-pressed="false">\u0422\u0435\u043c\u043d\u0430\u044f</button>' +
      '<button class="theme-switcher-btn" type="button" data-theme-option="light" aria-pressed="false">\u0421\u0432\u0435\u0442\u043b\u0430\u044f</button>';

    container.addEventListener("click", function (event) {
      var target = event.target;
      if (!(target instanceof HTMLElement)) return;
      if (!target.matches(".theme-switcher-btn")) return;

      var nextTheme = target.getAttribute("data-theme-option");
      if (!nextTheme) return;
      onSelect(nextTheme);
    });

    document.body.appendChild(container);
    updateButtons(currentTheme);
  }

  function init() {
    var playerPage = isPlayerPage();
    document.body.classList.add(playerPage ? "page-player" : "page-general");

    var defaultTheme = getDefaultTheme(playerPage);
    var initialTheme = normalizeTheme(getStoredTheme(), defaultTheme);
    applyTheme(initialTheme);

    buildSwitcher(initialTheme, function (nextThemeRaw) {
      var nextTheme = normalizeTheme(nextThemeRaw, defaultTheme);
      applyTheme(nextTheme);
      updateButtons(nextTheme);
      storeTheme(nextTheme);
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
