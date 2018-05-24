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
package io.micronaut.docs.security.securityRule.secured

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SecuredSpec extends Specification {

    @Shared
    Map<String, Object> config = [
            'spec.name': 'docsecured',
            'micronaut.security.enabled': true,
    ]

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "verify you can access an endpoint annotated with @Secured('isAnonymous()') without authentication"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/example/anonymous"))

        then:
        noExceptionThrown()
    }

    void "verify you can access an endpoint annotated with @Secured('isAuthenticated()') with an authenticated user"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/example/authenticated"))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        client.toBlocking().exchange(HttpRequest.GET("/example/authenticated").basicAuth("user", "password"))

        then:
        noExceptionThrown()
    }

    void "verify you can access an endpoint annotated with @Secured([\"ROLE_ADMIN\", \"ROLE_X\"]) with an authenticated user with one of those roles"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("/example/"))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        client.toBlocking().exchange(HttpRequest.GET("/example/admin")
                .basicAuth("user", "password"))

        then:
        e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN

        when:
        client.toBlocking().exchange(HttpRequest.GET("/example/admin")
                .basicAuth("admin", "password"))

        then:
        noExceptionThrown()
    }
}