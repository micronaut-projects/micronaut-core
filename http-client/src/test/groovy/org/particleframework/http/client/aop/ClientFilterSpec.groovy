/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client.aop

import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpResponse
import org.particleframework.http.MediaType
import org.particleframework.http.MutableHttpRequest
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Filter
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Header
import org.particleframework.http.client.Client
import org.particleframework.http.filter.ClientFilterChain
import org.particleframework.http.filter.HttpClientFilter
import org.particleframework.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class ClientFilterSpec extends Specification{

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test client filter includes header"() {
        given:
        MyApi myApi = context.getBean(MyApi)

        expect:
        myApi.name() == 'Fred'
    }

    @Controller('/filters')
    static class TestController {

        @Get(uri = '/name', produces = MediaType.TEXT_PLAIN)
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

    @Filter(patterns = '/filters/**', clients = 'otherClient')
    static class AnotherFilter implements HttpClientFilter {

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> request, ClientFilterChain chain) {
            request.header("X-Auth-Lastname", "Flintstone")
            return chain.proceed(request)
        }
    }
}
