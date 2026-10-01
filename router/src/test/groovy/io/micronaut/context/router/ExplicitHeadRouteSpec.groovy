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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import io.micronaut.web.router.exceptions.DuplicateRouteException
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * A {@code @Get} mapping also registers an implicit {@code HEAD} route so that a plain {@code GET}
 * handler answers {@code HEAD} requests. When a controller additionally declares an explicit
 * {@code @Head} route for the same URI, both routes match a {@code HEAD} request with identical
 * specificity, and resolution used to fail with a {@link DuplicateRouteException} (HTTP 400).
 *
 * <p>The implicit route is still registered; it simply loses the tie to the user's explicit
 * declaration. That matters because the two routes are frequently <em>not</em> interchangeable:
 * they may differ by {@code produces} or by {@code @Version}, in which case earlier stages of route
 * resolution pick between them and both must remain reachable.</p>
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13020")
class ExplicitHeadRouteSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder("test").build().start()

    @Shared
    Router router = context.getBean(Router)

    void "an explicit @Head wins over the implicit HEAD route derived from @Get for the same URI"() {
        when: "resolving a HEAD request the way the HTTP server does"
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.HEAD("/domainNames/example.dummy/availability"))

        then: "resolution succeeds - it previously threw DuplicateRouteException, surfaced as a 400"
        noExceptionThrown()
        match != null

        when:
        HttpResponse<?> response = match.invoke("example.dummy") as HttpResponse<?>

        then: "the explicitly annotated @Head method handles it"
        response.header("X-Handler") == "head"
    }

    void "GET on the shared URI still routes to the @Get method"() {
        when:
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/domainNames/example.dummy/availability"))

        then:
        match != null
        (match.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "get"
    }

    void "the implicit HEAD route is untouched on sibling URIs of a multi-URI @Get"() {
        when: "the URI shared with the explicit @Head"
        UriRouteMatch<Object, Object> shared = router.findClosest(HttpRequest.HEAD("/multi/example.dummy/availability"))

        then:
        shared != null
        (shared.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "head"

        when: "a sibling URI of the same @Get that has no explicit @Head"
        UriRouteMatch<Object, Object> sibling = router.findClosest(HttpRequest.HEAD("/multi/example.dummy/stock"))

        then: "the implicit HEAD route still answers"
        sibling != null
        (sibling.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "get"
    }

    void "@Get(headRoute = false) alongside an explicit @Head for the same URI"() {
        when:
        UriRouteMatch<Object, Object> head = router.findClosest(HttpRequest.HEAD("/noimplicit/example.dummy/availability"))

        then:
        head != null
        (head.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "head"

        when:
        UriRouteMatch<Object, Object> get = router.findClosest(HttpRequest.GET("/noimplicit/example.dummy/availability"))

        then:
        get != null
        (get.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "get"
    }

    void "the implicit HEAD route stays reachable when it is the only one producing the accepted media type"() {
        given: "a controller whose @Head produces text/plain and whose @Get produces application/json"

        when: "a HEAD request that accepts JSON - only the @Get route explicitly produces it"
        UriRouteMatch<Object, Object> json = router.findClosest(
                HttpRequest.HEAD("/negotiated/example.dummy/availability").accept(MediaType.APPLICATION_JSON_TYPE))

        then: "content negotiation resolves it to the implicit HEAD route, which must therefore still exist"
        json != null
        (json.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "get"

        when: "a HEAD request that accepts text/plain"
        UriRouteMatch<Object, Object> text = router.findClosest(
                HttpRequest.HEAD("/negotiated/example.dummy/availability").accept(MediaType.TEXT_PLAIN_TYPE))

        then: "the explicit @Head route answers"
        text != null
        (text.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "head"

        when: "a HEAD request with no preference - the two are genuinely tied"
        UriRouteMatch<Object, Object> any = router.findClosest(HttpRequest.HEAD("/negotiated/example.dummy/availability"))

        then: "the explicit declaration wins the tie"
        noExceptionThrown()
        any != null
        (any.invoke("example.dummy") as HttpResponse<?>).header("X-Handler") == "head"
    }

    void "genuinely ambiguous routes are still reported as duplicates"() {
        when: "two controllers declare the same GET URI"
        router.findClosest(HttpRequest.GET("/ambiguous/example.dummy/availability"))

        then:
        thrown(DuplicateRouteException)

        when: "and therefore two implicit HEAD routes for it as well"
        router.findClosest(HttpRequest.HEAD("/ambiguous/example.dummy/availability"))

        then: "the tie-break does not apply - neither candidate is explicit"
        thrown(DuplicateRouteException)
    }

    void "only the HEAD route derived from @Get is marked implicit"() {
        given:
        Map<String, Boolean> byMethod = router.uriRoutes().toList()
                .findAll { it.httpMethodName == "HEAD" && it.uriMatchTemplate.toString() == "/domainNames/{id}/availability" }
                .collectEntries { [(it.targetMethod.methodName): it.implicitHead] }

        expect:
        byMethod == ["availableSimple": false, "availableAdditional": true]
    }

    @Controller("/domainNames")
    static class DomainNameController {

        @Head("/{id}/availability")
        HttpResponse<?> availableSimple(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "head")
        }

        @Get("/{id}/availability")
        HttpResponse<?> availableAdditional(@PathVariable String id) {
            HttpResponse.ok().header("X-Handler", "get")
        }
    }

    @Controller("/multi")
    static class MultiUriController {

        @Head("/{id}/availability")
        HttpResponse<?> available(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "head")
        }

        @Get(uris = ["/{id}/availability", "/{id}/stock"])
        HttpResponse<?> get(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "get")
        }
    }

    @Controller("/noimplicit")
    static class NoImplicitHeadController {

        @Head("/{id}/availability")
        HttpResponse<?> available(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "head")
        }

        @Get(value = "/{id}/availability", headRoute = false)
        HttpResponse<?> get(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "get")
        }
    }

    @Controller("/negotiated")
    static class NegotiatedController {

        @Produces(MediaType.TEXT_PLAIN)
        @Head("/{id}/availability")
        HttpResponse<?> available(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "head")
        }

        @Produces(MediaType.APPLICATION_JSON)
        @Get("/{id}/availability")
        HttpResponse<?> get(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "get")
        }
    }

    @Controller("/ambiguous")
    static class AmbiguousControllerOne {

        @Get("/{id}/availability")
        HttpResponse<?> get(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "one")
        }
    }

    @Controller("/ambiguous")
    static class AmbiguousControllerTwo {

        @Get("/{id}/availability")
        HttpResponse<?> get(@PathVariable("id") String id) {
            HttpResponse.ok().header("X-Handler", "two")
        }
    }
}
