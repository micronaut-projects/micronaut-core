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
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification
import jakarta.inject.Inject

@Property(name = "spec.name", value = "RouteConditionControllerSpec")
@MicronautTest
class RouteConditionControllerSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    void "test route condition v1 is used by default"() {
        when:
        String response = client.toBlocking()
                .retrieve(HttpRequest.GET("/api/hello"))

        then:
        response == "Hello v1"
    }

    void "test route condition v2 is used when query param v=2"() {
        when:
        String response = client.toBlocking()
                .retrieve(HttpRequest.GET("/api/hello?v=2"))

        then:
        response == "Hello v2"
    }

    void "test route condition v1 is used when query param v is not 2"() {
        when:
        String response = client.toBlocking()
                .retrieve(HttpRequest.GET("/api/hello?v=3"))

        then:
        response == "Hello v1"
    }
}
