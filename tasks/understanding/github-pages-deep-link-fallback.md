# GitHub Pages deep-link fallback

GitHub Pages serves the frontend as a project site at `/TradingTool-3/`. Its static server returns a 404 when a user opens or refreshes a client-side console route directly. Add a `404.html` fallback that sends the requested URL to the Vite entry page, then restore the original URL before the React app chooses its route. The account root (`https://kush-remora.github.io/`) remains outside this repository's deployment scope.

Validation: the frontend production build succeeds, and its output contains `dist/404.html` alongside `dist/index.html`. The bundled app includes the route-restoration guard; a deployed deep-link check remains for the next GitHub Pages deployment.
