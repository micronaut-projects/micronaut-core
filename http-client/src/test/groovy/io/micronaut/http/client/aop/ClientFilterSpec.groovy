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
package io.micronaut.http.client.aop

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpVersion
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class ClientFilterSpec extends Specification{

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'spec.name': 'ClientFilterSpec',
    ])

    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test client filter includes header"() {
        given:
        MyApi myApi = context.getBean(MyApi)

        expect:
        myApi.name() == 'Fred'
    }

    void "test method-based client filter includes header"() {
        given:
        MyMethodApi myApi = context.getBean(MyMethodApi)

        expect:
        myApi.name() == 'Fred'
    }

    void "test a client with no service ids doesn't match a filter with a service id"() {
        given:
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange("/filters/name", String.class)

        then:
        response.body() == 'Fred'

        cleanup:
        client.close()
    }

    void "test a client doesn't match a filter with an excluded service id"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder([
                'spec.name': 'ClientFilterSpec',
                'micronaut.http.services.my-service.url': embeddedServer.getURL().toString()
        ]).start()
        HttpClient client = ctx.getBean(HttpClientRegistry).getClient(HttpVersion.HTTP_1_1, "my-service", null)

        when:
        HttpResponse<String> response = client.toBlocking().exchange("/excluded-filters/name", String.class)

        then:
        response.body() == 'Flintstone'

        cleanup:
        ctx.close()
    }

    void "test a client filter that throws an exception"() {
        given:
        HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange("/filter-exception/name", String.class)

        then:
        def ex = thrown(RuntimeException)
        ex.message == "from filter"

        cleanup:
        client.close()
    }

    void "test a client filter matching to the root"() {
        given:
        RootApi rootApi = context.getBean(RootApi)

        expect:
        rootApi.name() == 'processed'
    }

    void "test a root url matching a manually service client"() {
        given:
        ApplicationContext ctx = ApplicationContext.builder([
                'spec.name': 'ClientFilterSpec',
                'micronaut.http.services.my-service.url': embeddedServer.getURL().toString()
        ]).start()
        ServiceApi serviceApi = ctx.getBean(ServiceApi)

        expect:
        serviceApi.name() == 'processed'

        cleanup:
        ctx.close()
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Controller
    static class TestController {

        @Get(value = '/filters/name', produces = MediaType.TEXT_PLAIN)
        String name(@Header('X-Auth-Username') String username, @Header('X-Auth-Lastname') Optional<String> lastname) {
            return username + lastname.orElse('')
        }

        @Get(value = '/method-filters/name', produces = MediaType.TEXT_PLAIN)
        String name2(@Header('X-Auth-Username') String username, @Header('X-Auth-Lastname') Optional<String> lastname) {
            return username + lastname.orElse('')
        }

        @Get(value = '/excluded-filters/name', produces = MediaType.TEXT_PLAIN)
        String nameExcluded(@Header('X-Auth-Lastname') Optional<String> lastname) {
            return lastname.orElse('')
        }
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Controller
    static class RootController {

        @Get
        String name(@Header('X-Root-Filter') String value) {
            return value
        }
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Client('/filters')
    static interface MyApi {
        @Get(value = '/name', consumes = MediaType.TEXT_PLAIN)
        String name()
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Client('/method-filters')
    static interface MyMethodApi {
        @Get(value = '/name', consumes = MediaType.TEXT_PLAIN)
        String name()
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Client('/')
    static interface RootApi {

        @Get
        String name()
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Client('my-service')
    static interface ServiceApi {

        @Get
        String name()
    }

    // this filter should match
    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter('/filters/**')
    static class MyFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Username", "Fred")
            return chain.proceed(request)
        }
    }

    // this filter should match
    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @ClientFilter('/method-filters/**')
    static class MyMethodFilter {
        @RequestFilter
        void doFilter(MutableHttpRequest<?> request) {
            request.header("X-Auth-Username", "Fred")
        }
    }

    // this filter should not match the test
    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter('/surnames/**')
    static class SurnameFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Flintstone")
            return chain.proceed(request)
        }
    }

    // this filter should not match the test
    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter(patterns = '/filters/**', serviceId = 'otherClient')
    static class AnotherFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Flintstone")
            return chain.proceed(request)
        }
    }

    // this filter should match the test
    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter(patterns = '/excluded-filters/**', excludeServiceId = 'otherClient')
    static class Filter2 implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Flintstone")
            return chain.proceed(request)
        }
    }

    // this filter should not match the test
    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter(patterns = '/excluded-filters/**', excludeServiceId = 'my-service')
    static class Filter3 implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Fred")
            return chain.proceed(request)
        }
    }

    // this filter should not match the test
    @Filter(serviceId = 'myClient')
    static class MyClientFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Flintstone")
            return chain.proceed(request)
        }
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter("/**")
    static class RootFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Root-Filter", "processed")
            return chain.proceed(request)
        }
    }

    // this filter should not match the test
    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter('/filter-exception/**')
    static class ThrowingFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            throw new RuntimeException("from filter")
        }
    }

    void "filter always observes a response"() {
        given:
        ObservesResponseClient client = context.getBean(ObservesResponseClient)
        ObservesResponseFilter filter = context.getBean(ObservesResponseFilter)

        when:
        Mono.from(client.monoVoid()).block() == null
        then:
        filter.observedResponse != null
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Client('/observes-response')
    static interface ObservesResponseClient {

        @Get
        @SingleResult
        Publisher<Void> monoVoid()
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Filter("/observes-response/**")
    static class ObservesResponseFilter implements HttpClientFilter {
        HttpResponse<?> observedResponse

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            return Flux.from(chain.proceed(request)).doOnNext(r -> {
                observedResponse = r
            })
        }
    }

    @Requires(property = 'spec.name', value = "ClientFilterSpec")
    @Controller('/observes-response')
    static class ObservesResponseController {
        @Get
        String index() {
            return ""
        }
    }
}
