/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.server.routing

import io.kotlintest.specs.StringSpec
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

import javax.inject.Inject

@Property(name = "spec.name", value = "BackwardCompatibleControllerSpec")
@MicronautTest
class BackwardCompatibleControllerSpec {

    @Inject
    @field:Client("/")
    lateinit var client: HttpClient

    @Test
    fun testHelloWorldResponse() {
        var response = client.toBlocking()
                .retrieve(HttpRequest.GET<Any>("/hello/World"))
        assertEquals("Hello, World", response)

        response = client.toBlocking()
                .retrieve(HttpRequest.GET<Any>("/hello/person/John"))

        assertEquals("Hello, John", response)
    }
}
