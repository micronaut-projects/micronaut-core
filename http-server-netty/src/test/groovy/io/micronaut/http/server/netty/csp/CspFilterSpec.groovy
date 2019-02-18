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
package io.micronaut.http.server.netty.csp

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.server.csp.CspFilter
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class CspFilterSpec extends Specification {

    void "test no CSP configuration"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/csp')
        ).blockingFirst()
        def headerNames = response.headers.names()

        then:
        response.code() == HttpStatus.OK.code
        !headerNames.contains(CspFilter.CSP_HEADER)
        !headerNames.contains(CspFilter.CSP_REPORT_ONLY_HEADER)

        cleanup:
        embeddedServer.close()
    }

    void "test CSP no response header"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.server.csp.enabled': false,
                'micronaut.server.csp.reportOnly': false,
                'micronaut.server.csp.policyDirectives': "default-src self:"
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/csp')
        ).blockingFirst()
        def headerNames = response.headers.names()

        then:
        response.code() == HttpStatus.OK.code
        !headerNames.contains(CspFilter.CSP_HEADER)
        !headerNames.contains(CspFilter.CSP_REPORT_ONLY_HEADER)

        cleanup:
        embeddedServer.close()
    }

    void "test CSP response header"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.server.csp.enabled': true,
                'micronaut.server.csp.reportOnly': false,
                'micronaut.server.csp.policyDirectives': "default-src self:"
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/csp')
        ).blockingFirst()
        def headerNames = response.headers.names()

        then:
        response.code() == HttpStatus.OK.code
        response.header(CspFilter.CSP_HEADER) == "default-src self:"
        !headerNames.contains(CspFilter.CSP_REPORT_ONLY_HEADER)

        cleanup:
        embeddedServer.close()
    }

    void "test CSP response report only header"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.server.csp.enabled': true,
                'micronaut.server.csp.reportOnly': true,
                'micronaut.server.csp.policyDirectives': "default-src self:"
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange(
                HttpRequest.GET('/csp')
        ).blockingFirst()
        def headerNames = response.headers.names()

        then:
        response.code() == HttpStatus.OK.code
        response.header(CspFilter.CSP_REPORT_ONLY_HEADER) == "default-src self:"
        !headerNames.contains(CspFilter.CSP_HEADER)

        cleanup:
        embeddedServer.close()
    }

    @Controller('/csp')
    @Requires(property = 'spec.name', value = 'CspFilterSpec')
    static class TestController {

        @Get
        HttpResponse index() {
            HttpResponse.ok()
        }
    }
}
