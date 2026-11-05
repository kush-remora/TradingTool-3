import React from "react";
import ReactDOM from "react-dom/client";
import "antd/dist/reset.css";
import "./index.css";
import App from "./App";
import { BookGeneratorPage } from "./bookGenerator/BookGeneratorPage";

const publicBookGeneratorOnly = import.meta.env.VITE_PUBLIC_BOOK_GENERATOR_ONLY === "true";

function restoreGithubPagesRoute(): void {
  const pendingRoute = sessionStorage.getItem("tradingtool-github-pages-route");
  if (!pendingRoute) return;

  sessionStorage.removeItem("tradingtool-github-pages-route");
  const baseUrl = import.meta.env.BASE_URL;
  const requestedUrl = new URL(pendingRoute, window.location.origin);
  if (
    requestedUrl.origin !== window.location.origin ||
    !requestedUrl.pathname.startsWith(baseUrl)
  ) {
    return;
  }

  window.history.replaceState(
    {},
    "",
    `${requestedUrl.pathname}${requestedUrl.search}${requestedUrl.hash}`,
  );
}

function redirectPublicBuildToBookGenerator(): void {
  if (!publicBookGeneratorOnly) return;

  const baseUrl = import.meta.env.BASE_URL;
  const normalizedPath = window.location.pathname
    .replace(baseUrl, "")
    .replace(/^\/+|\/+$/g, "");
  if (normalizedPath === "book-generator") return;

  const params = new URLSearchParams(window.location.search);
  params.delete("redirect");
  params.delete("route");
  const search = params.toString();
  window.history.replaceState(
    {},
    "",
    `${baseUrl}book-generator${search ? `?${search}` : ""}${window.location.hash}`,
  );
}

function isBookGeneratorRoute(): boolean {
  const baseUrl = import.meta.env.BASE_URL;
  const path = window.location.pathname;
  const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;
  const normalized = internalPath.replace(/^\/+|\/+$/g, "");
  if (normalized === "book-generator") {
    return true;
  }
  const searchParams = new URLSearchParams(window.location.search);
  const redirect = searchParams.get("redirect") || "";
  if (redirect.includes("book-generator")) {
    return true;
  }
  if (searchParams.get("route") === "book-generator") {
    return true;
  }
  if (window.location.hash.includes("book-generator")) {
    return true;
  }
  return false;
}

restoreGithubPagesRoute();
redirectPublicBuildToBookGenerator();

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {publicBookGeneratorOnly || isBookGeneratorRoute() ? <BookGeneratorPage /> : <App />}
  </React.StrictMode>,
);
