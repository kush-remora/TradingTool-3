# Local Run Commands

## Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+ and npm

## Backend Only

1. Set database connection string (required by backend):

```bash
export SUPABASE_DB_URL="jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require"
```

2. Build and run backend:

```bash
mvn -f pom.xml -pl service -am package -DskipTests
java -jar service/target/service-0.1.0-SNAPSHOT.jar
```

3. Verify backend:

```bash
curl http://localhost:8080/health
```

## Frontend Only

1. Install frontend dependencies:

```bash
npm --prefix frontend install
```

2. Run frontend dev server:

```bash
npm --prefix frontend run dev -- --host 0.0.0.0 --port 5173
```

3. Open:

- http://localhost:5173

## Backend + Frontend Together

Use the helper script from repo root:

```bash
./run-local.sh
```

Press `Ctrl+C` to stop both processes.

If script says port already in use, free the port first:

```bash
lsof -ti tcp:8080 -sTCP:LISTEN | xargs kill -9
lsof -ti tcp:5173 -sTCP:LISTEN | xargs kill -9
```
