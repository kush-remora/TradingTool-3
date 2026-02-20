#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  echo
  echo "Stopping local services..."

  if [[ -n "$BACKEND_PID" ]] && kill -0 "$BACKEND_PID" 2>/dev/null; then
    kill "$BACKEND_PID" 2>/dev/null || true
  fi

  if [[ -n "$FRONTEND_PID" ]] && kill -0 "$FRONTEND_PID" 2>/dev/null; then
    kill "$FRONTEND_PID" 2>/dev/null || true
  fi

  if [[ -n "$BACKEND_PID" ]]; then
    wait "$BACKEND_PID" 2>/dev/null || true
  fi
  if [[ -n "$FRONTEND_PID" ]]; then
    wait "$FRONTEND_PID" 2>/dev/null || true
  fi
}

port_in_use() {
  local port="$1"
  lsof -ti "tcp:${port}" -sTCP:LISTEN >/dev/null 2>&1
}

if port_in_use 8080; then
  echo "Port 8080 is already in use. Stop the existing process and retry."
  exit 1
fi

if port_in_use 5173; then
  echo "Port 5173 is already in use. Stop the existing process and retry."
  exit 1
fi

if [[ ! -d "frontend/node_modules" ]]; then
  echo "Installing frontend dependencies..."
  npm --prefix frontend install
fi

trap cleanup EXIT INT TERM

echo "Starting backend on http://localhost:8080 ..."
echo "Building backend jar (service module)..."
mvn -f pom.xml -pl service -am package -DskipTests

BACKEND_JAR="$(find service/target -maxdepth 1 -type f -name 'service-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)"
if [[ -z "$BACKEND_JAR" ]]; then
  echo "Could not find backend jar under service/target."
  exit 1
fi

java -jar "$BACKEND_JAR" &
BACKEND_PID=$!

echo "Starting frontend on http://localhost:5173 ..."
npm --prefix frontend run dev -- --host 0.0.0.0 --port 5173 &
FRONTEND_PID=$!

echo
echo "Local stack is running:"
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:5173"
echo "Press Ctrl+C to stop both."

while true; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    wait "$BACKEND_PID"
    exit $?
  fi

  if ! kill -0 "$FRONTEND_PID" 2>/dev/null; then
    wait "$FRONTEND_PID"
    exit $?
  fi

  sleep 1
done
