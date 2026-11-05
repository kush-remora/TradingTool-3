import React from "react";
import ReactDOM from "react-dom/client";
import "antd/dist/reset.css";
import "./index.css";
import App from "./App";
import { BookGeneratorPage } from "../../evi/frontend/src/bookGenerator/BookGeneratorPage";

function isBookGeneratorRoute(): boolean {
  const baseUrl = import.meta.env.BASE_URL;
  const path = window.location.pathname;
  const internalPath = path.startsWith(baseUrl) ? path.slice(baseUrl.length) : path;
  return internalPath.replace(/^\/+|\/+$/g, "") === "book-generator";
}

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    {isBookGeneratorRoute() ? <BookGeneratorPage /> : <App />}
  </React.StrictMode>,
);
