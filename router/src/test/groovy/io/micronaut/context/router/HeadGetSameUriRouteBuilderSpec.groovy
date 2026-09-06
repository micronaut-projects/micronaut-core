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
package io.micronaut.context.router

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.PathVariable
import io.micronaut.web.router.Router
import io.micronaut.web.router.UriRouteMatch
import spock.lang.Issue
import spock.lang.Specification

/**
 * A controller that declares an explicit {@code @Head} method AND a {@code @Get} method for
 * the exact same URI used to be broken: the router auto-registers an implicit HEAD route for
 * every {@code @Get} method (so that a bare GET handler also answers HEAD requests). When an
 * explicit {@code @Head} route existed for the identical URI, that gave the router two HEAD
 * routes matching the same request with nothing to break the tie (same path template,
 * same specificity), so route resolution failed with a {@code DuplicateRouteException},
 * which the server translates into an HTTP 400 response.
 */
@Issue("https://github.com/micronaut-projects/micronaut-core/issues/13020")
class HeadGetSameUriRouteBuilderSpec extends Specification {

    void "an explicit @Head route is used - not the implicit HEAD route generated for @Get - when both map to the same URI"() {
        given:
        Router router = ApplicationContext.builder("test").build().start().getBean(Router)

        when: "resolving a HEAD request the same way the HTTP server does"
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.HEAD("/domainNames/example.dummy/availability"))

        then: "the route is resolved unambiguously (previously threw DuplicateRouteException -> HTTP 400)"
        noExceptionThrown()
        match != null

        when:
        HttpResponse<?> result = match.invoke("example.dummy") as HttpResponse<?>

        then: "the explicitly annotated @Head method handles the request, not the @Get method"
        result.status().code == 200
        result.header("X-Handler") == "head"
    }

    void "GET requests for the same URI still route to the @Get method"() {
        given:
        Router router = ApplicationContext.builder("test").build().start().getBean(Router)

        when:
        UriRouteMatch<Object, Object> match = router.findClosest(HttpRequest.GET("/domainNames/example.dummy/availability"))
        HttpResponse<?> result = match.invoke("example.dummy") as HttpResponse<?>

        then:
        match != null
        result.status().code == 200
        result.header("X-Handler") == "get"
    }

    void "an explicit @Head on one of multiple @Get uris only suppresses the implicit HEAD for that URI"() {
        given:
        Router router = ApplicationContext.builder("test").build().start().getBean(Router)

        when: "HEAD on the URI shared with @Head"
        UriRouteMatch<Object, Object> headMatch = router.findClosest(HttpRequest.HEAD("/multi/example.dummy/availability"))

        then:
        noExceptionThrown()
        headMatch != null
        HttpResponse<?> headResult = headMatch.invoke("example.dummy") as HttpResponse<?>
        headResult.status().code == 200
        headResult.header("X-Handler") == "head"

        when: "HEAD on a sibling URI from @Get(uris=[...]) without an explicit @Head"
        UriRouteMatch<Object, Object> implicitMatch = router.findClosest(HttpRequest.HEAD("/multi/example.dummy/stock"))

        then: "the implicit HEAD route is still registered and routes to the @Get method"
        noExceptionThrown()
        implicitMatch != null
        HttpResponse<?> implicitResult = implicitMatch.invoke("example.dummy") as HttpResponse<?>
        implicitResult.status().code == 200
        implicitResult.header("X-Handler") == "get"
    }

    void "@Get(headRoute = false) with a sibling @Head on the same URI registers only the explicit @Head"() {
        given:
        Router router = ApplicationContext.builder("test").build().start().getBean(Router)

        when:
        UriRouteMatch<Object, Object> headMatch = router.findClosest(HttpRequest.HEAD("/noimplicit/example.dummy/availability"))
        HttpResponse<?> headResult = headMatch.invoke("example.dummy") as HttpResponse<?>

        then:
        noExceptionThrown()
        headMatch != null
        headResult.status().code == 200
        headResult.header("X-Handler") == "head"

        when: "GET still routes to the @Get method"
        UriRouteMatch<Object, Object> getMatch = router.findClosest(HttpRequest.GET("/noimplicit/example.dummy/availability"))
        HttpResponse<?> getResult = getMatch.invoke("example.dummy") as HttpResponse<?>

        then:
        getMatch != null
        getResult.status().code == 200
        getResult.header("X-Handler") == "get"
    }

    @Controller("/domainNames")
    static class DomainNameController {

        @Head("/{id}/availability")
        HttpResponse<?> availableSimple(@PathVariable("id") String id) {
            return HttpResponse.ok().header("X-Handler", "head")
        }

        @Get("/{id}/availability")
        HttpResponse<?> availableAdditional(@PathVariable String id) {
            return HttpResponse.ok().header("X-Handler", "get")
        }
    }

    @Controller("/multi")
    static class MultiUriController {

        @Head("/{id}/availability")
        HttpResponse<?> available(@PathVariable("id") String id) {
            return HttpResponse.ok().header("X-Handler", "head")
        }

        @Get(uris = ["/{id}/availability", "/{id}/stock"])
        HttpResponse<?> getOrHead(@PathVariable("id") String id) {
            return HttpResponse.ok().header("X-Handler", "get")
        }
    }

    @Controller("/noimplicit")
    static class NoImplicitHeadController {

        @Head("/{id}/availability")
        HttpResponse<?> available(@PathVariable("id") String id) {
            return HttpResponse.ok().header("X-Handler", "head")
        }

        @Get(value = "/{id}/availability", headRoute = false)
        HttpResponse<?> get(@PathVariable("id") String id) {
            return HttpResponse.ok().header("X-Handler", "get")
        }
    }
}
