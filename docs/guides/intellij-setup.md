# IntelliJ + Maven Setup (Kotlin Migration)

This repo includes a Maven Kotlin service scaffold at:

- `pom.xml`
- `service/src/main/kotlin/com/tradingtool/Application.kt`
- `service/src/test/kotlin/com/tradingtool/ApplicationTest.kt`

## 1) IntelliJ Setup

1. Open `/Users/kushbhardwaj/Documents/github/TradingTool-3` in IntelliJ IDEA.
2. Ensure Kotlin plugin is enabled (bundled in IntelliJ IDEA).
3. Set Project SDK to JDK 21:
   - `File` -> `Project Structure` -> `Project`
   - `Project SDK`: `21`
   - `Project language level`: `SDK default`
4. Enable Maven auto-import when IntelliJ detects `pom.xml`.
5. In the Maven tool window, run `Lifecycle -> test`.
6. Create a Run Configuration:
   - Type: `Application`
   - Main class: `com.tradingtool.ApplicationKt`
   - Use classpath of module: `service`

## 2) Maven Setup

Prerequisites:

- JDK 21 installed.
- Maven 3.9+ available (or IntelliJ bundled Maven).

CLI commands:

```bash
# compile + test
mvn clean test

# run service
mvn -pl service -am exec:java -Dexec.mainClass=com.tradingtool.ApplicationKt
```

Smoke check:

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"ok"}
```
