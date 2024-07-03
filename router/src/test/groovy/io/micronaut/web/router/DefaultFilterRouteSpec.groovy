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
package io.micronaut.web.router

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.FilterOrder
import io.micronaut.http.filter.FilterPatternStyle
import io.micronaut.http.filter.GenericHttpFilter
import io.micronaut.http.filter.HttpFilter
import org.reactivestreams.Publisher
import spock.lang.Specification

import java.util.function.Supplier

class DefaultFilterRouteSpec extends Specification {

    void "test filter route matching with no methods specified"() {
        given:
        def filter = GenericHttpFilter.createLegacyFilter(new HttpFilter() {
            @Override
            Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
                return null
            }
        }, new FilterOrder.Fixed(0))

        when:
        def route = new DefaultFilterRoute("/foo", new Supplier<GenericHttpFilter>() {
            @Override
            GenericHttpFilter get() {
                return filter
            }
        })

        then: //all methods match
        route.match(HttpMethod.GET, URI.create('/foo')).isPresent()
        route.match(HttpMethod.POST, URI.create('/foo')).isPresent()
        route.match(HttpMethod.PUT, URI.create('/foo')).isPresent()
    }

    void "test filter route matching with methods specified"() {
        given:
        def filter = GenericHttpFilter.createLegacyFilter(new HttpFilter() {
            @Override
            Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
                return null
            }
        }, new FilterOrder.Fixed(0))

        when:
        def route = new DefaultFilterRoute("/foo", new Supplier<GenericHttpFilter>() {
            @Override
            GenericHttpFilter get() {
                return filter
            }
        }).methods(HttpMethod.POST, HttpMethod.PUT)

        then: //get does not match
        !route.match(HttpMethod.GET, '/foo').isPresent()
        route.match(HttpMethod.POST, '/foo').isPresent()
        route.match(HttpMethod.PUT, '/foo').isPresent()
    }

    void "test filter route matching with regex pattern style specified"() {
        given:
        def filter = GenericHttpFilter.createLegacyFilter(new HttpFilter() {
            @Override
            Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
                return null
            }
        }, new FilterOrder.Fixed(0))

        when:
        def route = new DefaultFilterRoute('/fo(a|o)$', new Supplier<GenericHttpFilter>() {
            @Override
            GenericHttpFilter get() {
                return filter
            }
        }).patternStyle(FilterPatternStyle.REGEX)

        then: //get does not match
        route.match(HttpMethod.GET, '/foo').isPresent()
        !route.match(HttpMethod.POST, '/foe').isPresent()
        route.match(HttpMethod.PUT, '/foa').isPresent()
    }
}
