package io.micronaut.http.client

import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client

// tests that nullable can compile
// issue: https://github.com/micronaut-projects/micronaut-core/issues/1080
@Client("/")
interface GreetingClient {
    @Get("/greeting{?name}")
    fun greet(name : String? ) : String
}