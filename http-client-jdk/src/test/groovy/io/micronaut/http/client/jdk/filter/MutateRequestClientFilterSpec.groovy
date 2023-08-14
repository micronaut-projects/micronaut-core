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
package io.micronaut.http.client.jdk.filter

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.http.uri.UriBuilder
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.annotation.Nullable
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/917")
@Property(name = 'spec.name', value = 'MutateRequestClientFilterSpec')
@MicronautTest
class MutateRequestClientFilterSpec extends Specification {
    @Inject
    FooClient myClient

    @Inject
    BarClient barClient

    void "test mutate outgoing URI"() {
        expect:
        myClient.simple() == "xxxxxxxxxxx"
        myClient.withQuery("foo") == "fooxxxxxxxxxxx"

        and:
        barClient.simple() == "xxxxxxxxxxx"
        barClient.withQuery("foo") == "fooxxxxxxxxxxx"
    }

    void "test mutate stream request URI"() {
        expect:
        myClient.stream().collectList().block().join("") == '["xxxxxxxxxxx"]'

        and:
        barClient.stream().collectList().block().join("") == '["xxxxxxxxxxx"]'
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @Client("/foo/filters/uri/test")
    static interface FooClient {
        @Get
        String simple()

        @Get("/foo{?q}")
        String withQuery(@Nullable String q)

        @Get("/stream")
        Flux<String> stream()
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @Client("/bar/filters/uri/test")
    static interface BarClient {
        @Get
        String simple()

        @Get("/foo{?q}")
        String withQuery(@Nullable String q)

        @Get("/stream")
        Flux<String> stream()
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @Controller('/foo/filters/uri/test')
    static class FooController {
        @Get
        String result(@QueryValue String signature) {
            signature
        }

        @Get('/foo')
        String query(@QueryValue String signature, @QueryValue String q) {
            q + signature
        }

        @Get('/stream')
        Flux<String> stream(@QueryValue String signature) {
            Flux.fromArray('"' + signature + '"')
        }
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @Controller('/bar/filters/uri/test')
    static class BarController {
        @Get
        String result(@QueryValue String signature) {
            signature
        }

        @Get('/foo')
        String query(@QueryValue String signature, @QueryValue String q) {
            q + signature
        }

        @Get('/stream')
        Flux<String> stream(@QueryValue String signature) {
            Flux.fromArray('"' + signature + '"')
        }
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @Filter('/foo/filters/uri/**')
    static class MyHttpClientFilter implements HttpClientFilter {
        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            chain.proceed(request.uri(UriBuilder.of(request.getUri())
                    .queryParam("signature", "xxxxxxxxxxx")
                    .build()))
        }
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @ClientFilter('/bar/filters/uri/**')
    static class MyFilter {
        @RequestFilter
        void filter(MutableHttpRequest<?> request) {
            request.uri(UriBuilder.of(request.getUri())
                    .queryParam("signature", "xxxxxxxxxxx")
                    .build())
        }
    }
}
