/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.streaming

import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.body.BodyElements
import io.micronaut.http.client.AsyncStreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

@MicronautTest
class AsyncHeadlineClientSpec {

    @Inject
    lateinit var headlineClient: AsyncHeadlineClient

    @Inject
    @field:Client("/")
    lateinit var client: AsyncStreamingHttpClient

    @Inject
    lateinit var suspendClient: SuspendHeadlineClient

    @Test
    fun declarativeClient() {
        // tag::declarative[]
        val headlines = headlineClient.streamHeadlines() // <1>
            .toCompletableFuture().get(10, TimeUnit.SECONDS)
        val first = headlines.next() // <2>
            .toCompletableFuture().get(10, TimeUnit.SECONDS)
        headlines.close() // <3>
        // end::declarative[]

        assertTrue(first.orElseThrow().text!!.startsWith("Latest Headline"))
    }

    @Test
    fun suspendClient() = runBlocking {
        val headlines = suspendClient.streamHeadlines()
        val first = headlines.next().await()
        headlines.close()

        assertTrue(first.orElseThrow().text!!.startsWith("Latest Headline"))
    }

    @Client("/streaming")
    interface SuspendHeadlineClient {

        @Get(value = "/headlines", processes = [MediaType.APPLICATION_JSON_STREAM])
        suspend fun streamHeadlines(): BodyElements<Headline>
    }

    @Test
    fun asyncStreamingClient() {
        // tag::async[]
        val first: CompletionStage<Optional<Headline>> = client.jsonStream(HttpRequest.GET<Any>("/streaming/headlines"), Headline::class.java) // <1>
            .thenCompose { headlines ->
                headlines.next() // <2>
                    .whenComplete { _, _ -> headlines.close() } // <3>
            }
        // end::async[]

        assertTrue(first.toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().text!!.startsWith("Latest Headline"))
    }
}
