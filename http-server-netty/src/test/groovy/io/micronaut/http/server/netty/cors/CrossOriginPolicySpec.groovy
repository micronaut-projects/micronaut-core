/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.netty.cors

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = CrossOriginPolicySpec.SPEC_NAME)
@Property(name = "micronaut.server.cors.enabled", value = "false")
@Property(name = "micronaut.server.cors.cross-origin-embedder-policy", value = "require-corp")
@Property(name = "micronaut.server.cors.cross-origin-resource-policy", value = "same-site")
@MicronautTest
class CrossOriginPolicySpec extends Specification {
    private static final String SPEC_NAME = "CrossOriginPolicySpec"

    @Inject
    @Client("/")
    HttpClient httpClient

    void "configured cross-origin policies are included without an Origin header"() {
        given:
        HttpRequest<?> request = HttpRequest.GET("/cross-origin-policy")

        and:
        !request.headers.contains(HttpHeaders.ORIGIN)

        when:
        HttpResponse<?> response = httpClient.toBlocking().exchange(request)

        then:
        response.headers.get(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY) == "require-corp"
        response.headers.get(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY) == "same-site"
    }

    void "configured cross-origin policies do not overwrite existing response headers"() {
        when:
        HttpResponse<?> response = httpClient.toBlocking().exchange(HttpRequest.GET("/cross-origin-policy-with-headers"))

        then:
        response.headers.get(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY) == "unsafe-none"
        response.headers.get(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY) == "same-origin"
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    static class TestController {

        @Get("/cross-origin-policy")
        String index() {
            "ok"
        }

        @Get("/cross-origin-policy-with-headers")
        HttpResponse<?> indexWithHeaders() {
            HttpResponse.ok("ok")
                .header(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY, "unsafe-none")
                .header(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY, "same-origin")
        }
    }
}
