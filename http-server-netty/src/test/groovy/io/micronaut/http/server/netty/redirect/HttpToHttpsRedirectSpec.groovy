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
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification

@Retry
class HttpToHttpsRedirectSpec extends Specification {

    @Shared
    int port = SocketUtils.findAvailableTcpPort()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
        'micronaut.server.port'                  : port,
        'micronaut.server.dual-protocol'         : true,
        'micronaut.server.http-to-https-redirect': true,
        'micronaut.ssl.enabled'                  : true,
        'micronaut.ssl.build-self-signed'        : true,
        'micronaut.http.client.follow-redirects' : false
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, new URL("http://localhost:$port"))

    void 'test http to https redirect when enabled'() {
        when:
        HttpResponse response = httpClient.toBlocking().exchange('/hello')

        then:
        response.status == HttpStatus.PERMANENT_REDIRECT
        response.header(HttpHeaders.LOCATION) == 'https://localhost:8443/hello'
        response.header(HttpHeaders.CONNECTION) == 'close'
    }
}
