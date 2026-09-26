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
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.exceptions.DuplicateRouteException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.stream.Collectors

class DefaultRouterMatchingSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(['spec.name': 'DefaultRouterMatchingSpec'])

    @Shared
    Router router = context.getBean(Router)

    void "no route matches"() {
        given:
        def request = HttpRequest.GET("/router-matching/missing/a/b/c")

        expect:
        router.findClosest(request) == null
        router.findAllClosest(request).isEmpty()
        router.find(request).count() == 0
    }

    void "a single route matches"() {
        given:
        def request = HttpRequest.GET("/router-matching/single/one")

        when:
        UriRouteMatch match = router.findClosest(request)
        List<UriRouteMatch> all = router.findAllClosest(request)

        then:
        match.methodName == 'one'
        all*.methodName == ['one']
        names(request) == ['one']
    }

    void "several routes match and the most specific one wins"() {
        given:
        def request = HttpRequest.GET("/router-matching/prec/exact")

        expect: "every candidate is found, the exact route first"
        names(request) == ['exact', 'template']
        router.findAllClosest(request)*.methodName == ['exact']
        router.findClosest(request).methodName == 'exact'
    }

    void "a template route matches when the exact one does not"() {
        given:
        def request = HttpRequest.GET("/router-matching/prec/other")

        expect:
        names(request) == ['template']
        router.findAllClosest(request)*.methodName == ['template']
        router.findClosest(request).methodName == 'template'
        router.findClosest(request).variableValues == [name: 'other']
    }

    void "a request with another method does not match"() {
        given:
        def request = HttpRequest.DELETE("/router-matching/single/one")

        expect:
        router.findClosest(request) == null
        router.findAllClosest(request).isEmpty()
        router.find(request).count() == 0
    }

    void "the Accept header selects between several candidates"() {
        expect:
        router.findClosest(HttpRequest.GET("/router-matching/neg/item").accept(accept))?.methodName == expected
        router.findAllClosest(HttpRequest.GET("/router-matching/neg/item").accept(accept))*.methodName == (expected ? [expected] : [])

        where:
        accept                           | expected
        MediaType.APPLICATION_JSON_TYPE  | 'json'
        MediaType.TEXT_PLAIN_TYPE        | 'text'
        MediaType.APPLICATION_XML_TYPE   | null
    }

    void "several candidates producing different types are ambiguous without an Accept header"() {
        given:
        def request = HttpRequest.GET("/router-matching/neg/item")

        expect:
        names(request) as Set == ['json', 'text'] as Set
        router.findAllClosest(request).size() == 2

        when:
        router.findClosest(request)

        then:
        thrown(DuplicateRouteException)
    }

    void "the Content-Type header selects between several candidates"() {
        given:
        def request = HttpRequest.POST("/router-matching/neg/data", "body").contentType(contentType)

        expect:
        router.findClosest(request)?.methodName == expected
        names(request) == (expected ? [expected] : [])

        where:
        contentType                      | expected
        MediaType.APPLICATION_JSON_TYPE  | 'consumeJson'
        MediaType.TEXT_PLAIN_TYPE        | 'consumeText'
        MediaType.APPLICATION_XML_TYPE   | null
    }

    void "the Accept and Content-Type headers combine with template precedence"() {
        given:
        def request = HttpRequest.POST("/router-matching/neg/data/exact", "body")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .accept(MediaType.TEXT_PLAIN_TYPE)

        expect: "the JSON candidates are filtered out and the exact text route beats the text template"
        names(request) == ['textExact', 'textTemplate']
        router.findAllClosest(request)*.methodName == ['textExact']
        router.findClosest(request).methodName == 'textExact'
    }

    private List<String> names(HttpRequest<?> request) {
        router.find(request).map { it.methodName }.collect(Collectors.toList())
    }

    @Requires(property = 'spec.name', value = 'DefaultRouterMatchingSpec')
    @Controller("/router-matching/single")
    static class SingleController {
        @Get("/one")
        String one() {
            "one"
        }
    }

    @Requires(property = 'spec.name', value = 'DefaultRouterMatchingSpec')
    @Controller("/router-matching/prec")
    static class PrecedenceController {
        @Get("/{name}")
        String template(String name) {
            name
        }

        @Get("/exact")
        String exact() {
            "exact"
        }
    }

    @Requires(property = 'spec.name', value = 'DefaultRouterMatchingSpec')
    @Controller("/router-matching/neg")
    static class NegotiationController {
        @Get(value = "/item", produces = MediaType.APPLICATION_JSON)
        String json() {
            "{}"
        }

        @Get(value = "/item", produces = MediaType.TEXT_PLAIN)
        String text() {
            "text"
        }

        @Post(value = "/data", consumes = MediaType.APPLICATION_JSON)
        String consumeJson(@Body String body) {
            body
        }

        @Post(value = "/data", consumes = MediaType.TEXT_PLAIN)
        String consumeText(@Body String body) {
            body
        }

        @Post(value = "/data/{name}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
        String jsonTemplate(String name, @Body String body) {
            body
        }

        @Post(value = "/data/{name}", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String textTemplate(String name, @Body String body) {
            body
        }

        @Post(value = "/data/exact", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String textExact(@Body String body) {
            body
        }
    }
}
