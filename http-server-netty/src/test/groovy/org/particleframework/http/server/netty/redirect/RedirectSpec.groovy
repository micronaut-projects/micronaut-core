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
package org.particleframework.http.server.netty.redirect

import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpHeaders
import org.particleframework.http.HttpResponse
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.client.RxHttpClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class RedirectSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup RxHttpClient httpClient = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


    void 'test permanent redirect'() {
        when:
        HttpResponse response = httpClient.toBlocking().exchange('/redirect/permanent')

        then:
        response.status == HttpStatus.PERMANENT_REDIRECT
        response.header(HttpHeaders.LOCATION)
        !response.header(HttpHeaders.CONTENT_TYPE)
    }

    @Controller("/redirect")
    static class RedirectController {

        @Get("/permanent")
        HttpResponse permanent() {
            HttpResponse.permanentRedirect(URI.create('/redirected'))
        }

        @Get("/temporary")
        HttpResponse temporary() {
            HttpResponse.temporaryRedirect(URI.create('/redirected'))
        }
    }
}
