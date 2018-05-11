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
package io.micronaut.security.token.jwt.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OauthControllerPathConfigurableSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'refreshpathconfigurable',
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.endpoints.oauth.enabled': true,
            'micronaut.security.endpoints.oauth.path': '/newtoken',
    ], 'test')

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


    void "OauthController is not accessible at /oauth/access_token but at /newtoken"() {
        given:
        TokenRefreshRequest creds = new TokenRefreshRequest('foo', 'XXXXXXXXXX')

        expect:
        embeddedServer.applicationContext.getBean(OauthController.class)

        when:
        client.toBlocking().exchange(HttpRequest.POST('/oauth/access_token', creds))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        client.toBlocking().exchange(HttpRequest.POST('/newtoken', creds))

        then:
        e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
    }
}
