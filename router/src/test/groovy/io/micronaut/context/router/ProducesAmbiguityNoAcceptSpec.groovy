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
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.exceptions.DuplicateRouteException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Two routes on the same URI that differ only by {@code @Produces}: without an Accept preference the
 * route producing the default response type (JSON) is chosen instead of failing as a duplicate.
 */
class ProducesAmbiguityNoAcceptSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'ProducesAmbiguityNoAcceptSpec'])

    @Shared
    Router router = context.getBean(Router)

    void "Accept #accept resolves to the #expected route"() {
        given:
        def request = HttpRequest.GET("/produces-ambiguity")
        if (accept != null) {
            request.header(HttpHeaders.ACCEPT, accept)
        }

        when:
        UriRouteMatch<Object, Object> match = router.findClosest(request)

        then:
        match.invoke() == expected

        where:
        accept                     | expected
        null                       | "json"
        MediaType.ALL              | "json"
        MediaType.TEXT_PLAIN       | "text"
        MediaType.APPLICATION_JSON | "json"
    }

    void "routes that do not produce JSON stay ambiguous without an Accept preference"() {
        when:
        router.findClosest(HttpRequest.GET("/produces-ambiguity/no-json"))

        then:
        thrown(DuplicateRouteException)
    }

    void "an explicit non-JSON Accept still picks its route among non-JSON routes"() {
        expect:
        router.findClosest(HttpRequest.GET("/produces-ambiguity/no-json").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML)).invoke() == "html"
    }

    @Controller("/produces-ambiguity")
    @Requires(property = "spec.name", value = "ProducesAmbiguityNoAcceptSpec")
    static class ProducesController {

        @Get
        @Produces(MediaType.TEXT_PLAIN)
        String text() {
            "text"
        }

        @Get
        @Produces(MediaType.APPLICATION_JSON)
        String json() {
            "json"
        }

        @Get("/no-json")
        @Produces(MediaType.TEXT_PLAIN)
        String noJsonText() {
            "text"
        }

        @Get("/no-json")
        @Produces(MediaType.TEXT_HTML)
        String noJsonHtml() {
            "html"
        }
    }
}
