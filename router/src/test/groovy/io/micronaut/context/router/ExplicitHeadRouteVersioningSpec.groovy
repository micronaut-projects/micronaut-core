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
package io.micronaut.context.router

import io.micronaut.context.ApplicationContext
import io.micronaut.core.version.annotation.Version
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.PathVariable
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * An explicit {@code @Head} and a {@code @Get} can map to the same URI while being scoped to different
 * API versions. Version filtering runs after route matching, so the implicit HEAD route derived from
 * the {@code @Get} must survive route registration: it is the only route serving {@code HEAD} for its
 * own version.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13020")
class ExplicitHeadRouteVersioningSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder("test")
            .properties([
                    'micronaut.router.versioning.enabled'       : true,
                    'micronaut.router.versioning.header.enabled': true
            ])
            .build()
            .start()

    @Shared
    Router router = context.getBean(Router)

    void "HEAD resolves to the versioned @Get route for version 1"() {
        when:
        UriRouteMatch<Object, Object> match = router.findClosest(
                HttpRequest.HEAD("/versioned/example.dummy/availability").header("X-API-VERSION", "1"))

        then: "the implicit HEAD route derived from the v1 @Get is the only candidate for that version"
        noExceptionThrown()
        match != null
        (match.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "get-v1"
    }

    void "HEAD resolves to the explicit @Head route for version 2"() {
        when:
        UriRouteMatch<Object, Object> match = router.findClosest(
                HttpRequest.HEAD("/versioned/example.dummy/availability").header("X-API-VERSION", "2"))

        then:
        noExceptionThrown()
        match != null
        (match.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "head-v2"
    }

    void "GET still resolves to the versioned @Get route"() {
        when:
        UriRouteMatch<Object, Object> match = router.findClosest(
                HttpRequest.GET("/versioned/example.dummy/availability").header("X-API-VERSION", "1"))

        then:
        match != null
        (match.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "get-v1"
    }

    @Controller("/versioned")
    static class VersionedController {

        @Version("2")
        @Head("/{id}/availability")
        HttpResponse<?> availableV2(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "head-v2")
        }

        @Version("1")
        @Get("/{id}/availability")
        HttpResponse<?> availableV1(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "get-v1")
        }
    }
}
