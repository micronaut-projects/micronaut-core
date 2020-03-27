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
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.Single
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton

class ClientFilterStereotypeSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(EmbeddedServer).applicationContext

    void "test declarative client matching"() {
        when:
        UnmarkedClient unmarkedClient = ctx.getBean(UnmarkedClient)

        then:
        unmarkedClient.echo() == "echo URL" // not intercepted by

        when:
        MarkedClient markedClient = ctx.getBean(MarkedClient)

        then:
        markedClient.echoPost() == "echo Intercepted Post URL"
        markedClient.echo() == "echo Intercepted URL"
    }

    void "low-level client filter matching"() {
        given:
        ClientBeans clientBeans = ctx.getBean(ClientBeans)

        expect:
        clientBeans.annotatedClient.toBlocking().retrieve('/') == "echo Intercepted URL"
        clientBeans.client.toBlocking().retrieve('/') == "echo URL"
        clientBeans.annotatedClient.toBlocking().retrieve(HttpRequest.POST('/', '')) == "echo Intercepted Post URL"
    }

    @Singleton
    static class ClientBeans {
        HttpClient annotatedClient
        HttpClient client

        ClientBeans(
                @MarkerStereotypeAnnotation @Client('/filters/marked') HttpClient annotatedClient,
                @Client('/filters/marked') HttpClient client
        ) {
            this.client = client
            this.annotatedClient = annotatedClient
        }
    }

    @Client("/filters/marked")
    @MarkerStereotypeAnnotation
    static interface MarkedClient {
        @Get("/")
        String echo()

        @Post("/")
        String echoPost()
    }

    @Client("/filters/marked")
    static interface UnmarkedClient {
        @Get("/")
        String echo()
    }

    @Controller('/filters/')
    static class UriController {

        @Get('/marked')
        String marked() {
            return "echo"
        }

        @Post('/marked')
        String post() {
            return "echo"
        }
    }

    @MarkerStereotypeAnnotation
    @Singleton
    static class MarkerFilter implements HttpClientFilter {

        @Override
        int getOrder() {
            0
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return Flowable.fromPublisher(chain.proceed(request))
                    .map({ HttpResponse response ->
                        HttpResponse.ok(response.body().toString() + " Intercepted")
                    })
        }
    }

    @MarkerStereotypeAnnotation(methods = HttpMethod.POST)
    @Singleton
    static class MarkerPostFilter implements HttpClientFilter {

        @Override
        int getOrder() {
            1
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return Flowable.fromPublisher(chain.proceed(request))
                    .map({ HttpResponse response ->
                        HttpResponse.ok(response.body().toString() + " Post")
                    })
        }
    }

    @Singleton
    @Filter("/filters/marked")
    static class UrlFilter implements HttpClientFilter {

        @Override
        int getOrder() {
            2
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return Flowable.fromPublisher(chain.proceed(request))
                    .map({ HttpResponse response ->
                        HttpResponse.ok(response.body().toString() + " URL")
                    })
        }
    }

}
