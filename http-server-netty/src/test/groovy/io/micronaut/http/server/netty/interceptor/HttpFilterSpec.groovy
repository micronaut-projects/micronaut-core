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

import io.micronaut.context.annotation.AliasFor
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.http.HttpAttributes
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.FilterMatcher
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.test.annotation.MicronautTest
import io.reactivex.Flowable
import io.reactivex.Single
import org.reactivestreams.Publisher
import spock.lang.Specification

import javax.inject.Inject
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@MicronautTest
@Property(name = 'spec.name', value = "HttpFilterSpec")
class HttpFilterSpec extends Specification {

    @Inject
    @Client("/")
    RxHttpClient rxClient

    @Inject
    ContentTypeFilter contentTypeFilter

    void "test interceptor execution and order - write replacement"() {
        when:
        rxClient.retrieve("/secure").blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN
    }

    void "test interceptor execution and order - proceed"() {
        when:
        HttpResponse<String> response = rxClient.exchange("/secure?username=fred", String).blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Test") == "Foo Test"
        response.body.isPresent()
        response.body.get() == "Authenticated: fred"
    }

    void "test a filter on the root url"() {
        when:
        HttpResponse response = rxClient.exchange("/").blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        !response.headers.contains("X-Matched-Filter")
    }

    void "test a filter on a reactive url"() {
        when:
        HttpResponse response = rxClient.exchange("/reactive").blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        !response.headers.contains("X-Matched-Filter")
    }

    void "test a filter on matched with filter matcher URI"() {
        when:
        HttpResponse response = rxClient.exchange("/matched").blockingFirst()

        then:
        response.status == HttpStatus.OK
        response.headers.get("X-Root-Filter") == "processed"
        response.headers.contains("X-Matched-Filter")
    }

    void "test content type is available in server filters"() {
        when:
        HttpResponse<String> response = rxClient.exchange("/simple", String.class).blockingFirst()

        then:
        response.body() == "simple"
        response.status == HttpStatus.OK
        response.contentType.get() == MediaType.APPLICATION_JSON_TYPE
        contentTypeFilter.contentTypeFound.get()
    }

    @Requires(property = 'spec.name', value = "HttpFilterSpec")
    @Filter("/**")
    static class RootFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flowable.fromPublisher(chain.proceed(request)).doOnNext({ response ->
                if (response.status.code < 300) {
                    assert response.getAttribute(HttpAttributes.ROUTE_MATCH,
                            AnnotationMetadata.class).isPresent()
                }
                response.header("X-Root-Filter", "processed")
            })
        }
    }

    @Filter("/simple")
    @Requires(property = 'spec.name', value = "HttpFilterSpec")
    static class ContentTypeFilter implements HttpServerFilter {

        AtomicBoolean contentTypeFound = new AtomicBoolean()

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flowable.fromPublisher(chain.proceed(request)).doOnNext({ response ->
               contentTypeFound.compareAndSet(false, response.contentType.isPresent())
            })
        }
    }

    @Filter("/**")
    @MarkerStereotypeAnnotation
    @Requires(property = 'spec.name', value = "HttpFilterSpec")
    static class MatchedFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flowable.fromPublisher(chain.proceed(request)).doOnNext({ response ->
                response.header("X-Matched-Filter", "processed")
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
        Single<HttpResponse> reactive() {
            Single.just(HttpResponse.ok())
        }

        @Get("/matched")
        @MarkerStereotypeAnnotation
        HttpResponse matched() {
            HttpResponse.ok()
        }

        @Get("/simple")
        String simple() {
            "simple"
        }

    }


}
@FilterMatcher
@interface MarkerStereotypeAnnotation {

    @AliasFor(member = "methods", annotation = FilterMatcher.class)
    HttpMethod[] methods() default [];
}
