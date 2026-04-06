# HttpRequestExecutor Refactoring Recommendations

## Current Implementation Review

### ✅ What's Good
1. **Clean separation of concerns** — `HttpRequestExecutor` interface allows swapping implementations
2. **Already uses coroutines** — `.sendAsync().await()` is the right pattern for non-blocking IO
3. **Type-safe request/response models** — `HttpRequestData` and `HttpResponseData` are clean DTOs

### ⚠️ Issues Identified

#### 1. **Missing Thread Pool Context**
Currently `JdkHttpRequestExecutor.execute()` runs on the **caller's thread** (likely Jetty threads from JAX-RS).

```kotlin
// Current implementation - runs on caller's thread
override suspend fun execute(request: HttpRequestData): HttpResponseData {
    val response = httpClient
        .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
        .await()  // ← Suspends, but resumes on same context
    return HttpResponseData(...)
}
```

**Problem**: While `sendAsync()` doesn't block, the request building and response parsing happen on the Jetty thread.

#### 2. **HttpClient Thread Pool Not Configured**
JDK HttpClient uses its own internal thread pool (virtual threads in JDK 21+), but you're not explicitly managing this.

#### 3. **Missing `isConfigured()` in TelegramApiClient**
`TelegramSender.isConfigured()` calls `telegramApiClient.isConfigured()`, but this method doesn't exist in `TelegramApiClient`.

---

## Recommended Refactoring

### Architecture: Two Thread Pool Strategy

```
JAX-RS Resource (Jetty thread)
    ↓
Service.suspendFunction()
    ↓
HttpRequestExecutor.execute() (switches to Dispatchers.IO)
    ↓
HttpClient.sendAsync() (uses internal HTTP client thread pool)
    ↓
Result returned through coroutine context
    ↓
Back to Jetty thread for response serialization
```

### Solution 1: Use Dispatchers.IO for HTTP Execution

**File**: `core/src/main/kotlin/com/tradingtool/core/http/HttpRequestExecutor.kt`

```kotlin
package com.tradingtool.core.http

import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.future.await
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface HttpRequestExecutor {
    /**
     * Execute HTTP request on Dispatchers.IO thread pool.
     * Uses JDK HttpClient's internal thread pool for actual network IO.
     */
    suspend fun execute(request: HttpRequestData): HttpResponseData
}

@Singleton
class JdkHttpRequestExecutor @Inject constructor(
    private val httpClient: HttpClient,
) : HttpRequestExecutor {

    override suspend fun execute(request: HttpRequestData): HttpResponseData = withContext(Dispatchers.IO) {
        // Request building on IO thread
        val httpRequest = buildHttpRequest(request)

        // Network call using HttpClient's internal pool
        val response = httpClient
            .sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
            .await()

        // Response parsing on IO thread
        HttpResponseData(
            statusCode = response.statusCode(),
            body = response.body().orEmpty(),
        )
    }

    private fun buildHttpRequest(request: HttpRequestData): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(request.uri)

        request.timeout?.let { timeout ->
            builder.timeout(timeout)
        }

        request.headers.forEach { (key, value) ->
            builder.header(key, value)
        }

        val bodyPublisher = request.body?.let { body ->
            HttpRequest.BodyPublishers.ofByteArray(body)
        } ?: HttpRequest.BodyPublishers.noBody()

        return builder
            .method(request.method, bodyPublisher)
            .build()
    }
}
```

**Why this works:**
- `withContext(Dispatchers.IO)` offloads request building from Jetty threads
- `HttpClient.sendAsync()` still uses its own internal thread pool for network IO
- Response parsing happens on IO thread before returning to caller's context

---

### Solution 2: Configure HttpClient Properly

**File**: `app/src/main/kotlin/com/tradingtool/TradingToolApplication.kt`

```kotlin
import java.net.http.HttpClient
import java.time.Duration

// In run() method:
val httpClient = HttpClient.newBuilder()
    .version(HttpClient.Version.HTTP_2)
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()
```

**Benefits:**
- HTTP/2 support for multiplexing (faster for multiple Telegram requests)
- Explicit timeouts prevent hanging requests
- Auto-redirect handling

---

### Solution 3: Add `isConfigured()` to TelegramApiClient

**File**: `core/src/main/kotlin/com/tradingtool/core/telegram/TelegramApiClient.kt`

```kotlin
@Singleton
class TelegramApiClient @Inject constructor(
    private val botToken: String,
    private val chatId: String,
    private val httpRequestExecutor: HttpRequestExecutor,
    private val json: Json,
) {
    private val configured: Boolean = botToken.isNotBlank() && chatId.isNotBlank()

    fun isConfigured(): Boolean = configured

    // Remove init block with require() — let isConfigured() handle this
    // Or keep init block but make parameters nullable and check in isConfigured()

    // ... rest of code
}
```

**Alternative** (if you want nullable params):

```kotlin
@Singleton
class TelegramApiClient @Inject constructor(
    private val botToken: String?,
    private val chatId: String?,
    private val httpRequestExecutor: HttpRequestExecutor,
    private val json: Json,
) {
    fun isConfigured(): Boolean =
        !botToken.isNullOrBlank() && !chatId.isNullOrBlank()

    internal suspend fun sendText(text: String): TelegramApiCallResult {
        check(isConfigured()) { "Telegram is not configured" }
        // ... rest of code
    }
}
```

---

## Advanced: Custom Dispatcher for HTTP Calls

If you want **finer control** over the HTTP thread pool (separate from general IO pool):

**File**: `core/src/main/kotlin/com/tradingtool/core/http/HttpDispatchers.kt`

```kotlin
package com.tradingtool.core.http

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Dedicated dispatcher for HTTP client operations.
 * Separate from Dispatchers.IO to avoid contention with database operations.
 */
object HttpDispatchers {
    /**
     * Dispatcher backed by a fixed thread pool for external HTTP calls.
     * Size: CPU cores * 2 (sufficient for IO-bound work)
     */
    val HTTP: CoroutineDispatcher by lazy {
        val coreCount = Runtime.getRuntime().availableProcessors()
        Executors.newFixedThreadPool(
            coreCount * 2,
            { runnable ->
                Thread(runnable, "http-pool-thread").apply {
                    isDaemon = true
                }
            }
        ).asCoroutineDispatcher()
    }
}
```

**Usage**:
```kotlin
override suspend fun execute(request: HttpRequestData): HttpResponseData =
    withContext(HttpDispatchers.HTTP) {
        // HTTP operations on dedicated pool
    }
```

**Trade-offs:**
- ✅ Isolates HTTP latency from DB operations
- ✅ Predictable thread pool sizing
- ❌ More complexity (another pool to manage)
- ❌ Overkill for small apps

**Recommendation**: Start with `Dispatchers.IO`, add custom dispatcher only if you see thread pool contention.

---

## Summary of Changes

### Minimal Changes (Do These First)
1. ✅ Add `withContext(Dispatchers.IO)` to `JdkHttpRequestExecutor.execute()`
2. ✅ Add `isConfigured()` method to `TelegramApiClient`
3. ✅ Configure `HttpClient` with timeouts and HTTP/2

### Optional Enhancements
4. ⚪ Extract request building to separate function (cleaner code)
5. ⚪ Add custom `HttpDispatchers.HTTP` if you see thread pool issues
6. ⚪ Add retry logic for transient network failures
7. ⚪ Add request/response logging for debugging

---

## Code Flow After Refactoring

```
[JAX-RS Resource - Jetty Thread]
TelegramResource.sendText() calls async { }
    ↓
[Coroutine - Jetty Thread Context]
TelegramSender.sendText() → validation logic
    ↓
[Coroutine - Jetty Thread Context]
TelegramApiClient.sendText() → build request data
    ↓
[Switch to Dispatchers.IO]
HttpRequestExecutor.execute() → withContext(Dispatchers.IO)
    ↓
[IO Thread]
Build HttpRequest from HttpRequestData
    ↓
[IO Thread suspends, HttpClient pool takes over]
httpClient.sendAsync().await()
    ↓
[HttpClient internal thread pool]
Actual network IO (connect, send, receive)
    ↓
[Resume on IO Thread]
Parse response, create HttpResponseData
    ↓
[Resume on original coroutine context]
TelegramApiClient parses JSON, returns TelegramApiCallResult
    ↓
[Jetty Thread Context]
TelegramSender converts to TelegramSendResult
    ↓
[Jetty Thread]
JAX-RS serializes to JSON response
```

---

## Testing the Changes

```kotlin
// In TelegramSenderTest.kt
@Test
fun `sendText should execute on IO dispatcher`() = runBlocking {
    val currentThread = Thread.currentThread().name

    val result = telegramSender.sendText(
        TelegramSendTextRequest(text = "Test")
    )

    // Verify we're not blocking the test thread
    assertNotEquals(currentThread, Thread.currentThread().name)
}
```
