package com.tradingtool.resources

/**
 * Single source of truth for all JAX-RS resource classes.
 *
 * Referenced by:
 * - ServiceModule  — to bind each class as a Guice singleton
 * - Application    — to register each instance with Jersey
 *
 * Adding a new resource = add it here only.
 */
val ALL_RESOURCE_CLASSES: List<Class<*>> = listOf(
    TradeResource::class.java,
    StrategyResource::class.java,
    ConsoleV2Resource::class.java,
    IntegrationResource::class.java,
    StockResource::class.java,
)
