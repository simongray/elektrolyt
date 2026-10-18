// The colours that the listener chose, on the page before anything renders,
// from the copy that themed! in main.cljs keeps in localStorage, rather than
// the device's until the library is restored. A file of its own, since the
// server's Content-Security-Policy allows no inline script.
try {
  const theme = localStorage.getItem("dk.simongray.podcast-app.theme");
  if (theme === "light" || theme === "dark") {
    document.documentElement.dataset.theme = theme;
  }
} catch (e) {
  // no storage, e.g. in a private window: the device's colours, until then
}
