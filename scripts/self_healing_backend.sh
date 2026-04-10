#!/bin/bash
set -e

LOG_FILE="backend_startup.log"
PORT=8080

echo "Attempting to build and start backend..." | tee "$LOG_FILE"

# Step 1: Build
if ! mvn -f pom.xml -pl service -am clean package -DskipTests >> "$LOG_FILE" 2>&1; then
    echo "BUILD_FAILED"
    exit 1
fi

# Step 2: Find JAR
BACKEND_JAR=$(find service/target -maxdepth 1 -type f -name 'service-*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | head -n 1)
if [[ -z "$BACKEND_JAR" ]]; then
    echo "JAR_NOT_FOUND" >> "$LOG_FILE"
    echo "JAR_NOT_FOUND"
    exit 1
fi

# Step 3: Run in background
LOCAL_CONFIG_FILE="service/src/main/resources/localconfig.yaml"
java -jar "$BACKEND_JAR" server "$LOCAL_CONFIG_FILE" >> "$LOG_FILE" 2>&1 &
BACKEND_PID=$!

# Step 4: Monitor for success or failure
echo "Monitoring backend (PID: $BACKEND_PID)..."
for i in {1..30}; do
    if ! kill -0 $BACKEND_PID 2>/dev/null; then
        echo "BACKEND_CRASHED"
        exit 1
    fi
    
    if lsof -ti "tcp:$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
        echo "BACKEND_STARTED_SUCCESSFULLY"
        exit 0
    fi
    sleep 2
done

echo "BACKEND_TIMEOUT"
kill $BACKEND_PID 2>/dev/null || true
exit 1
