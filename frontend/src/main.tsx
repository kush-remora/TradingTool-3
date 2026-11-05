import React from "react";
import ReactDOM from "react-dom/client";
import "antd/dist/reset.css";
import "./index.css";
import App from "./App";
import { BookGeneratorPage } from "./bookGenerator/BookGeneratorPage";

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

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {isBookGeneratorRoute() ? <BookGeneratorPage /> : <App />}
  </React.StrictMode>,
);
