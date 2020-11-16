package io.micronaut.http.client

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.http.client.annotation.Client

@Client("/")
interface SuspendClient {

    @Put
    suspend fun call(newState: String): String

    @Get
    suspend fun notFound(): HttpResponse<String?>
}
