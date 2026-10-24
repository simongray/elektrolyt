// The colours and the contrast that the listener chose, on the page before
// anything renders, from the copies that themed! in ui/document.cljs keeps
// in localStorage, rather than the device's until the library is restored.
// A file of its own, since the server's Content-Security-Policy allows no
// inline script.
try {
  const theme = localStorage.getItem("dk.simongray.elektrolyt.theme");
  if (theme === "light" || theme === "dark") {
    document.documentElement.dataset.theme = theme;
  }
  const contrast = localStorage.getItem("dk.simongray.elektrolyt.contrast");
  if (contrast === "standard" || contrast === "more") {
    document.documentElement.dataset.contrast = contrast;
  }
} catch (e) {
  // no storage, e.g. in a private window: the device's colours, until then
}
