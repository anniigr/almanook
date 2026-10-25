// Applies the saved (or OS) theme before first paint to avoid a flash. Kept as an external file so the
// Content-Security-Policy can forbid inline scripts.
;(function () {
  try {
    var saved = localStorage.getItem('almanook.theme')
    var prefersLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches
    var theme = saved === 'light' || saved === 'dark' ? saved : prefersLight ? 'light' : 'dark'
    document.documentElement.dataset.theme = theme
  } catch (e) {
    document.documentElement.dataset.theme = 'dark'
  }
})()
