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
package io.micronaut.http.client.filter

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.Nullable

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/917")
class MutateRequestClientFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'MutateRequestClientFilterSpec'])

    @Shared
    MyClient myClient = embeddedServer.applicationContext.getBean(MyClient)

    void "test mutate outgoing URI"() {
        expect:
        myClient.simple() == "xxxxxxxxxxx"
        myClient.withQuery("foo") == "fooxxxxxxxxxxx"
    }

    void "test mutate stream request URI"() {
        expect:
        myClient.stream().blockFirst() == "xxxxxxxxxxx"
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @Client("/filters/uri/test")
    static interface MyClient {
        @Get("/")
        String simple()

        @Get("/foo{?q}")
        String withQuery(@Nullable String q)

        @Get("/stream")
        Flux<String> stream()
    }

    @Requires(property = 'spec.name', value = 'MutateRequestClientFilterSpec')
    @Controller('/filters/uri/test')
    static class UriController {
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
    @Filter('/filters/uri/**')
    static class MyFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            UriBuilder builder = UriBuilder.of(request.getUri())
            builder.queryParam("signature", "xxxxxxxxxxx")
            return chain.proceed(request.uri(builder.build()))
        }
    }
}
