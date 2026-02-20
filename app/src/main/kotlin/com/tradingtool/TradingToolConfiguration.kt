package com.tradingtool

import com.fasterxml.jackson.annotation.JsonProperty
import io.dropwizard.core.Configuration
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

class TradingToolConfiguration : Configuration() {
    @Valid
    @NotNull
    @JsonProperty("database")
    var database: DatabaseSettings = DatabaseSettings()

    @Valid
    @NotNull
    @JsonProperty("telegram")
    var telegram: TelegramSettings = TelegramSettings()

    @Valid
    @NotNull
    @JsonProperty("cors")
    var cors: CorsSettings = CorsSettings()
}

data class DatabaseSettings(
    @JsonProperty("url")
    var url: String = "",

    @JsonProperty("user")
    var user: String = "",

    @JsonProperty("password")
    var password: String = "",
)

data class TelegramSettings(
    @JsonProperty("botToken")
    var botToken: String = "",

    @JsonProperty("chatId")
    var chatId: String = "",

    @JsonProperty("webhookSecret")
    var webhookSecret: String = "",
)

data class CorsSettings(
    @JsonProperty("allowedOrigins")
    var allowedOrigins: String = "https://kushb2.github.io,http://localhost:5173",
)
