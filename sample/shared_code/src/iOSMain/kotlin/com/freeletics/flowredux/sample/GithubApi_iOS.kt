package com.freeletics.flowredux.sample

import com.freeletics.flowredux.sample.shared.GithubApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.ios.Ios
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import kotlinx.serialization.json.Json

val githubApi_iOS = GithubApi(
    httpClient = HttpClient(Ios) {

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.ALL
        }

        install(JsonFeature) {
            serializer = KotlinxSerializer(Json.nonstrict)
        }
    }
)