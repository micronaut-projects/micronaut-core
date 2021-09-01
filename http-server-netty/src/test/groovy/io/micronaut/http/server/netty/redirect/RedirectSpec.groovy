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
package io.micronaut.http.server.netty.redirect

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class RedirectSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL(), new DefaultHttpClientConfiguration(followRedirects: false))

    void 'test permanent redirect'() {
        when:
        HttpResponse response = httpClient.toBlocking().exchange('/redirect/permanent')

        then:
        response.status == HttpStatus.PERMANENT_REDIRECT
        response.header(HttpHeaders.LOCATION)
        !response.header(HttpHeaders.CONTENT_TYPE)
    }

    void 'test redirect'() {
        when:
        HttpResponse response = httpClient.toBlocking().exchange('/redirect/regular')

        then:
        response.status == HttpStatus.MOVED_PERMANENTLY
        response.header(HttpHeaders.LOCATION)
        !response.header(HttpHeaders.CONTENT_TYPE)
    }

    @Controller("/redirect")
    static class RedirectController {

        @Get("/permanent")
        HttpResponse permanent() {
            HttpResponse.permanentRedirect(URI.create('/redirected'))
        }

        @Get("/regular")
        HttpResponse regular() {
            HttpResponse.redirect(URI.create('/redirected'))
        }
        @Get("/temporary")
        HttpResponse temporary() {
            HttpResponse.temporaryRedirect(URI.create('/redirected'))
        }
    }
}
