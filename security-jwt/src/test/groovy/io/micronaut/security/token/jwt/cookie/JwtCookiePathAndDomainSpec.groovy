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
package io.micronaut.security.token.jwt.cookie

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment

import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JwtCookiePathAndDomainSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': 'jwtcookie',
                    'micronaut.http.client.followRedirects': false,
                    'micronaut.security.enabled': true,
                    'micronaut.security.endpoints.login.enabled': true,
                    'micronaut.security.endpoints.logout.enabled': true,
                    'micronaut.security.token.jwt.enabled': true,
                    'micronaut.security.token.jwt.bearer.enabled': false,
                    'micronaut.security.token.jwt.cookie.enabled': true,
                    'micronaut.security.token.jwt.cookie.cookie-path': "/path",
                    'micronaut.security.token.jwt.cookie.cookie-domain': "example.com",
                    'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            ], Environment.TEST)

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "verify jwt cookie path and domain is set from configuration"() {

        when:
        HttpRequest loginRequest = HttpRequest.POST('/login', new LoginForm(username: 'sherlock', password: 'password'))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)

        HttpResponse<String> loginRsp = client.toBlocking().exchange(loginRequest, String)

        then:
        loginRsp.status().code == 303

        when:
        String cookie = loginRsp.getHeaders().get('Set-Cookie')
        println cookie
        then:
        cookie
        cookie.contains('JWT=')
        cookie.contains('Domain=example.com')
        cookie.contains('Path=/path;')

        when:
        String sessionId = cookie.substring('JWT='.size(), cookie.indexOf(';'))
        HttpRequest request = HttpRequest.GET('/').cookie(Cookie.of('JWT', sessionId))
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then:
        rsp.status().code == 200
        rsp.body()
        rsp.body().contains('sherlock')

        when:
        HttpRequest logoutRequest = HttpRequest.POST('/logout', "").cookie(Cookie.of('JWT', sessionId))
        HttpResponse<String> logoutRsp = client.toBlocking().exchange(logoutRequest, String)

        then:
        logoutRsp.status().code == 303

        when:
        String logoutCookie = logoutRsp.getHeaders().get('Set-Cookie')

        then:
        logoutCookie
        logoutCookie.contains('JWT=')
        logoutCookie.contains('Domain=example.com')
        logoutCookie.contains('Path=/path;')
        logoutCookie.contains('Max-Age=0;')
    }

}
