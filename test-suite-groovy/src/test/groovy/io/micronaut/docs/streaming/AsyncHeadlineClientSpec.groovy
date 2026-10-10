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
import io.micronaut.http.body.BodyElements
import io.micronaut.http.client.AsyncStreamingHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.CompletionStage
import java.util.concurrent.TimeUnit

@MicronautTest
class AsyncHeadlineClientSpec extends Specification {

    @Inject
    AsyncHeadlineClient headlineClient

    @Inject
    @Client("/")
    AsyncStreamingHttpClient client

    void "declarative client"() {
        when:
        // tag::declarative[]
        BodyElements<Headline> headlines = headlineClient.streamHeadlines() // <1>
            .toCompletableFuture().get(10, TimeUnit.SECONDS)
        Optional<Headline> first = headlines.next() // <2>
            .toCompletableFuture().get(10, TimeUnit.SECONDS)
        headlines.close() // <3>
        // end::declarative[]

        then:
        first.orElseThrow().text.startsWith("Latest Headline")
    }

    void "async streaming client"() {
        when:
        // tag::async[]
        CompletionStage<Optional<Headline>> first = client.jsonStream(HttpRequest.GET("/streaming/headlines"), Headline) // <1>
            .thenCompose { headlines ->
                headlines.next() // <2>
                    .whenComplete { headline, error -> headlines.close() } // <3>
            }
        // end::async[]

        then:
        first.toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().text.startsWith("Latest Headline")
    }
}
