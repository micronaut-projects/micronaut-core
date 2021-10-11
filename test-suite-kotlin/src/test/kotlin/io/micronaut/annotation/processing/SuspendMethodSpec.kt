/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.annotation.processing

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import javax.inject.Inject
import kotlin.test.assertEquals

// issue https://github.com/micronaut-projects/micronaut-core/issues/5396
@MicronautTest
class SuspendMethodSpec {

    @Inject
    @field:Client("/demo")
    lateinit var client: HttpClient

    @Test
    fun testSyncMethodReturnTypeAny() {
        val res = Flux.from(client
            .retrieve(
                HttpRequest.GET<Any>("/sync/any"),
                Any::class.java
            )).blockFirst()

        assertEquals("sync any", res)
    }

    @Test
    fun testSyncMethodReturnTypeString() {
        val res = Flux.from(client
            .retrieve(
                HttpRequest.GET<String>("/sync/string"),
                Any::class.java
            )).blockFirst()

        assertEquals("sync string", res)
    }


    @Test
    fun testAsyncMethodReturnTypeAny() {
        val res = Flux.from(client
            .retrieve(
                HttpRequest.GET<Any>("/async/any"),
                Any::class.java
            )).blockFirst()

        assertEquals("async any", res)
    }

    @Test
    fun testAsyncMethodReturnTypeString() {
        val res = Flux.from(client
            .retrieve(
                HttpRequest.GET<String>("/async/string"),
                Any::class.java
            )).blockFirst()

        assertEquals("async string", res)
    }


}
