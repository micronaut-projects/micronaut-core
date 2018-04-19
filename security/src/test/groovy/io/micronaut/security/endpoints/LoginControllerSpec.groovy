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

package io.micronaut.security.endpoints

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authentication.Authenticator
import io.micronaut.security.authentication.UserDetails
import io.micronaut.security.authentication.UsernamePasswordCredentials
import io.micronaut.security.token.generator.AccessRefreshTokenGenerator
import io.micronaut.security.token.configuration.TokenConfiguration
import io.micronaut.security.token.render.BearerAccessRefreshToken
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class LoginControllerSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            [
                    "micronaut.security.enabled": true,
                    "micronaut.security.endpoints.login": true,
                    "micronaut.security.token.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa'
            ], 'test')

    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = context.createBean(HttpClient, embeddedServer.getURL())

    def "if valid credentials authenticate"() {
        expect:
        context.getBean(AuthenticationProviderThrowingException.class)
        context.getBean(AuthenticationProviderUserPassword.class)

        when:
        def creds = new UsernamePasswordCredentials('user', 'password')
        HttpResponse rsp = client.toBlocking().exchange(HttpRequest.POST('/login', creds), BearerAccessRefreshToken)

        then:
        rsp.status() == HttpStatus.OK
        rsp.body().accessToken
        rsp.body().refreshToken
        rsp.body().username
        rsp.body().roles == []
        rsp.body().expiresIn
    }

    def "invoking login with GET, returns unauthorized"() {
        expect:
        context.getBean(AuthenticationProviderThrowingException.class)
        context.getBean(AuthenticationProviderUserPassword.class)

        when:
        def creds = new UsernamePasswordCredentials('user', 'password')
        client.toBlocking().exchange(HttpRequest.GET('/login').body(creds))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }

    def "if invalid credentials unauthorized"() {
        expect:
        context.getBean(AuthenticationProviderThrowingException.class)
        context.getBean(AuthenticationProviderUserPassword.class)

        when:
        def creds = new UsernamePasswordCredentials('user', 'bogus')
        client.toBlocking().exchange(HttpRequest.POST('/login', creds))

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED
    }


    def "if authenticator returns empty, return empty"() {
        given:
        def accessRefreshTokenGenerator = Mock(AccessRefreshTokenGenerator)
        def tokenConfiguration = Mock(TokenConfiguration)
        def authenticator = Stub(Authenticator) {
            authenticate(_) >> Optional.empty()
        }
        LoginController loginController = new LoginController(accessRefreshTokenGenerator, tokenConfiguration, authenticator)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        def rsp = loginController.authenticate(creds)

        then:
        !rsp.isPresent()
    }

    def "if authenticator returns user details authenticate, it is returned"() {
        given:
        def accessRefreshTokenGenerator = Mock(AccessRefreshTokenGenerator)
        def tokenConfiguration = Mock(TokenConfiguration)
        def authenticator = Stub(Authenticator) {
            authenticate(_) >> Optional.of(new UserDetails('admin', ['ROLE_USER']))
        }
        LoginController loginController = new LoginController(accessRefreshTokenGenerator, tokenConfiguration, authenticator)

        when:
        def creds = new UsernamePasswordCredentials('admin', 'admin')
        def rsp = loginController.authenticate(creds)

        then:
        rsp.isPresent()
        rsp.get().username == 'admin'
        rsp.get().roles == ['ROLE_USER']
    }
}
