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
package io.micronaut.http.server.netty.interceptor

import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.http.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.FilterMatcher
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@MicronautTest
@Property(name = 'spec.name', value = "HttpFilterSpec")
class HttpFilterSpec extends Specification {

    @Inject
    @Client("/")
    HttpClient rxClient

    void "test interceptor execution and order - write replacement"() {
        when:
        rxClient.toBlocking().retrieve("/secure")

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test interceptor execution and order - proceed"() {
        when:
        HttpResponse<String> response = rxClient.toBlocking().exchange("/secure?username=fred", String)

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Test") == "Foo Test"
        response.body.isPresent()
        response.body.get() == "Authenticated: fred"
    }

    void "test a filter on the root url"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange("/")

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        !response.headers.contains("X-Matched-Filter")
    }

    void "test a filter on a reactive url"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange("/reactive")

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        !response.headers.contains("X-Matched-Filter")
    }

    void "test a filter on matched with filter matcher URI"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange("/matched")

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        response.headers.contains("X-Matched-Filter")
    }

    void "test two filters on matched with filter matcher URI"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange("/matchedtwice")

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        response.headers.contains("X-Matched-Filter")
        response.headers.contains("X-Another-Matched-Filter")
    }

    void "test a filter on indirect matched with filter matcher URI"() {
        when:
        HttpResponse response = rxClient.toBlocking().exchange("/indirectlymatched")

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        response.headers.contains("X-Matched-Filter")
        response.headers.contains("X-Another-Matched-Filter")
    }

    @Requires(property = 'spec.name', value = "HttpFilterSpec")
    @Filter("/**")
    static class RootFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(chain.proceed(request)).doOnNext({ response ->
                if (response.status.code < 300) {
                    assert response.getAttribute(HttpAttributes.ROUTE_MATCH,
                            AnnotationMetadata.class).isPresent()
                }
                response.header("X-Root-Filter", "processed")
            })
        }
    }

    @Filter("/**")
    @MarkerStereotypeAnnotation
    @Requires(property = 'spec.name', value = "HttpFilterSpec")
    static class MatchedFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(chain.proceed(request)).doOnNext({ response ->
                response.header("X-Matched-Filter", "processed")
            })
        }
    }

    @Filter("/**")
    @AnotherMarkerStereotypeAnnotation
    @Requires(property = 'spec.name', value = "HttpFilterSpec")
    static class AnotherMatchedFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.from(chain.proceed(request)).doOnNext({ response ->
                response.header("X-Another-Matched-Filter", "processed")
            })
        }
    }


    @Controller
    @Requires(property = 'spec.name', value = "HttpFilterSpec")
    static class RootController {

        @Get
        HttpResponse root() {
            HttpResponse.ok()
        }

        @Get("/reactive")
        @SingleResult
        Publisher<HttpResponse> reactive() {
            Mono.just(HttpResponse.ok())
        }

        @Get("/matched")
        @MarkerStereotypeAnnotation
        HttpResponse matched() {
            HttpResponse.ok()
        }

        @Get("/matchedtwice")
        @MarkerStereotypeAnnotation
        @AnotherMarkerStereotypeAnnotation
        HttpResponse matchedTwice() {
            HttpResponse.ok()
        }

        @Get("/indirectlymatched")
        @IndirectMarkerStereotypeAnnotation
        HttpResponse indirectlyMatched() {
            HttpResponse.ok()
        }
    }
}

@FilterMatcher
@interface MarkerStereotypeAnnotation {

    @AliasFor(member = "methods", annotation = FilterMatcher.class)
    HttpMethod[] methods() default [];
}
@FilterMatcher
@interface AnotherMarkerStereotypeAnnotation {

    @AliasFor(member = "methods", annotation = FilterMatcher.class)
    HttpMethod[] methods() default [];
}
@MarkerStereotypeAnnotation
@AnotherMarkerStereotypeAnnotation
@interface IndirectMarkerStereotypeAnnotation {
}
