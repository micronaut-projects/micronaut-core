/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.Client
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
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test client filter includes header"() {
        given:
        MyApi myApi = context.getBean(MyApi)

        expect:
        myApi.name() == 'Fred'
    }

    @Controller('/filters')
    static class TestController {

        @Get(value = '/name', produces = MediaType.TEXT_PLAIN)
        String name(@Header('X-Auth-Username') String username, @Header('X-Auth-Lastname') Optional<String> lastname) {
            return username + lastname.orElse('')
        }
    }

    @Client('/filters')
    static interface MyApi {
        @Get('/name')
        String name()
    }


    // this filter should match
    @Filter('/filters/**')
    static class MyFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Username", "Fred")
            return chain.proceed(request)
        }
    }

    // this filter should not match the test
    @Filter('/surnames/**')
    static class SurnameFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Flintstone")
            return chain.proceed(request)
        }
    }

    @Filter(patterns = '/filters/**', serviceId = 'otherClient')
    static class AnotherFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Flintstone")
            return chain.proceed(request)
        }
    }
}
