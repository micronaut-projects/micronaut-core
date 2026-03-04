/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.docs.server.routing

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

@Property(name = "spec.name", value = "RouteConditionControllerSpec")
@MicronautTest
class RouteConditionControllerSpec {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun testRouteConditionV1() {
        val response = client.toBlocking()
                .retrieve(HttpRequest.GET<Any>("/api/hello"))
        assertEquals("Hello v1", response)
    }

    @Test
    fun testRouteConditionV2() {
        val response = client.toBlocking()
                .retrieve(HttpRequest.GET<Any>("/api/hello?v=2"))
        assertEquals("Hello v2", response)
    }

    @Test
    fun testRouteConditionFallsBackToV1ForUnmatchedVersion() {
        val response = client.toBlocking()
                .retrieve(HttpRequest.GET<Any>("/api/hello?v=3"))
        assertEquals("Hello v1", response)
    }
}
