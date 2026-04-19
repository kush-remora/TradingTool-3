package com.tradingtool.core.http

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import java.net.http.HttpClient as JdkHttpClient

/**
 * Guice module for HTTP client dependencies.
 * Provides SuspendHttpClient interface (suspend-based), JsonHttpClient, and ObjectMapper.
 */
class CoreHttpModule : AbstractModule() {

    @Provides
    @Singleton
    fun provideJdkHttpClient(): JdkHttpClient = JdkHttpClient.newBuilder().build()

    @Provides
    @Singleton
    fun provideSuspendHttpClient(jdkHttpClient: JdkHttpClient): SuspendHttpClient =
        JdkHttpClientImpl(jdkHttpClient, HttpClientConfig())

    @Provides
    @Singleton
    fun provideJsonHttpClient(
        httpClient: SuspendHttpClient,
        objectMapper: ObjectMapper,
    ): JsonHttpClient = JsonHttpClient(httpClient, objectMapper)
}
