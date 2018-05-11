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
package io.micronaut.security.token.jwt.bearer

import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import spock.lang.Shared
import spock.lang.Specification

class BearerTokenReaderSpec extends Specification {

    @Shared
    BearerTokenConfiguration config = Stub(BearerTokenConfiguration) {
        isEnabled() >> true
        getHeaderName() >> 'Authorization'
        getPrefix() >> 'Bearer'
    }

    @Shared
    BearerTokenReader bearerTokenReader = new BearerTokenReader(config)

    def extractTokenFromAuthorization() {
        expect:
        bearerTokenReader.extractTokenFromAuthorization('Bearer XXX').get() == 'XXX'

        and:
        !bearerTokenReader.extractTokenFromAuthorization('BearerXXX').isPresent()

        and:
        !bearerTokenReader.extractTokenFromAuthorization('XXX').isPresent()
    }

    def "if authorization header not present returns empty"() {
        given:
        def request = HttpRequest.create(HttpMethod.GET, '/')

        expect:
        !bearerTokenReader.findToken(request).isPresent()
    }

    def "findTokenAtAuthorizationHeader parses header correctly"() {
        given:
        def request = HttpRequest.create(HttpMethod.GET, '/').header('Authorization', 'Bearer XXX')

        expect:
        bearerTokenReader.findToken(request).get() == 'XXX'
    }
}
