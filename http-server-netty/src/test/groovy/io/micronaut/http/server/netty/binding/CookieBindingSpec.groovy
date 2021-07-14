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
package io.micronaut.http.server.netty.binding

import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.cookie.Cookies
import io.micronaut.http.server.netty.AbstractMicronautSpec
import reactor.core.publisher.Flux
import spock.lang.Unroll

/**
 * Created by graemerocher on 25/07/2017.
 */
class CookieBindingSpec extends AbstractMicronautSpec {

    @Unroll
    void "test bind HTTP cookies for URI #uri"() {
        expect:
        HttpRequest<?> request = HttpRequest.GET(uri)
        for (header in headers) {
            request = request.header(header.key, header.value)
        }
        rxClient.toBlocking().retrieve(request) == result

        where:
        uri                | result              | headers
        '/cookie/all'      | "Cookie Value: bar" | ['Cookie': 'myVar=bar']
        '/cookie/simple'   | "Cookie Value: bar" | ['Cookie': 'myVar=bar']
        '/cookie/optional' | "Cookie Value: 10"  | ['Cookie': 'myVar=10']
        '/cookie/optional' | "Cookie Value: -1"  | ['Cookie': 'myVar=foo']
        '/cookie/optional' | "Cookie Value: -1"  | [:]
    }

    void "test set HTTP cookies for client"() {
        setup:
        CookieClient client = embeddedServer.applicationContext.getBean(CookieClient)

        when:
        def result = client.simple("foo")

        then:
        result == "Cookie Value: foo"
    }

    void "test set HTTP cookies with custom names"() {
        setup:
        CookieClient client = embeddedServer.applicationContext.getBean(CookieClient)

        when:
        def result = client.custom("foo")

        then:
        result == "Cookie Value: foo"
    }

    @Client('/cookie')
    static interface CookieClient extends CookieApi {
    }

    @Controller("/cookie")
    static class CookieController implements CookieApi {

        @Override
        String simple(@CookieValue String myVar) {
            "Cookie Value: $myVar"
        }

        @Override
        String custom(@CookieValue('custom') String myVar) {
            "Cookie Value: $myVar"
        }

        @Override
        String optional(@CookieValue Optional<Integer> myVar) {
            "Cookie Value: ${myVar.orElse(-1)}"
        }

        @Override
        String all(Cookies cookies) {
            "Cookie Value: ${cookies.get('myVar')?.value}"
        }
    }


    static interface CookieApi {

        @Get("/simple")
        String simple(@CookieValue String myVar)

        @Get("/custom")
        String custom(@CookieValue('custom') String myVar)

        @Get("/optional")
        String optional(@CookieValue Optional<Integer> myVar)

        @Get("/all")
        String all(Cookies cookies)
    }

}
