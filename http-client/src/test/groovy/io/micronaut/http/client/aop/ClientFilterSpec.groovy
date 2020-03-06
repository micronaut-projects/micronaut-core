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
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.ClientFilterChain
import io.micronaut.http.filter.HttpClientFilter
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class ClientFilterSpec extends Specification{

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
            'spec.name': 'ClientFilterSpec',
    ])

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test client filter includes header"() {
        given:
        MyApi myApi = context.getBean(MyApi)

        expect:
        myApi.name() == 'Fred'
    }

    void "test a client with no service ids doesn't match a filter with a service id"() {
        given:
        RxHttpClient client = context.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        HttpResponse<String> response = client.toBlocking().exchange("/filters/name", String.class)

        then:
        response.body() == 'Fred'

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
        ApplicationContext ctx = ApplicationContext.build([
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
    @Controller('/filters')
    static class TestController {

        @Get(value = '/name', produces = MediaType.TEXT_PLAIN)
        String name(@Header('X-Auth-Username') String username, @Header('X-Auth-Lastname') Optional<String> lastname) {
            return username + lastname.orElse('')
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
}
