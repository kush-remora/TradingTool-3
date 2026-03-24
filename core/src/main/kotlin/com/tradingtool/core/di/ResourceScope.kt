package com.tradingtool.core.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Application-level coroutine scope for resource-level async operations.
 *
 * Shared across all resource classes to use a single, properly managed scope.
 * Must be cancelled at application shutdown to ensure clean coroutine cleanup.
 */
class ResourceScope {
    val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun shutdown() {
        ioScope.cancel()
    }
}
