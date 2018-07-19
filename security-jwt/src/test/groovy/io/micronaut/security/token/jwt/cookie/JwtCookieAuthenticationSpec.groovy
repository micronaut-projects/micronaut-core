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
package io.micronaut.security.token.jwt.cookie

import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.cookie.Cookie
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.endpoints.LoginController
import io.micronaut.security.endpoints.LogoutController
import io.micronaut.security.token.jwt.bearer.BearerTokenReader
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

class JwtCookieAuthenticationSpec extends GebSpec {

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
                    'micronaut.security.token.jwt.cookie.loginFailureTargetUrl': '/login/authFailed',
                    'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            ], 'test')

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    def "verify jwt cookie authentication works without Geb"() {
        context.getBean(HomeController.class)
        context.getBean(LoginAuthController.class)
        context.getBean(AuthenticationProviderUserPassword.class)
        context.getBean(AuthenticationProviderUserPassword.class)
        context.getBean(LoginController.class)
        context.getBean(LogoutController.class)
        context.getBean(JwtCookieLoginHandler.class)
        context.getBean(JwtCookieClearerLogoutHandler.class)
        context.getBean(SignatureConfiguration.class)
        context.getBean(SignatureConfiguration.class, Qualifiers.byName("generator"))

        when:
        context.getBean(EncryptionConfiguration.class)

        then:
        thrown(NoSuchBeanException)

        when:
        context.getBean(BearerTokenReader.class)

        then:
        thrown(NoSuchBeanException)

        when:
        HttpRequest request = HttpRequest.GET('/')
        HttpResponse<String> rsp = client.toBlocking().exchange(request, String)

        then:
        rsp.status().code == 200
        rsp.body()
        rsp.body().contains('You are not logged in')

        when:
        HttpRequest loginRequest = HttpRequest.POST('/login', new LoginForm(username: 'foo', password: 'foo'))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)

        HttpResponse<String> loginRsp = client.toBlocking().exchange(loginRequest, String)

        then:
        loginRsp.status().code == 303

        and: 'login fails, cookie is not set'
        !loginRsp.getHeaders().get('Set-Cookie')

        when:
        loginRequest = HttpRequest.POST('/login', new LoginForm(username: 'sherlock', password: 'password'))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)

        loginRsp = client.toBlocking().exchange(loginRequest, String)

        then:
        loginRsp.status().code == 303

        when:
        String cookie = loginRsp.getHeaders().get('Set-Cookie')
        println cookie
        then:
        cookie
        cookie.contains('JWT=')

        when:
        String sessionId = cookie.substring('JWT='.size(), cookie.indexOf(';'))
        request = HttpRequest.GET('/').cookie(Cookie.of('JWT', sessionId))
        rsp = client.toBlocking().exchange(request, String)

        then:
        rsp.status().code == 200
        rsp.body()
        rsp.body().contains('sherlock')
    }

    @IgnoreIf({ !sys['geb.env']})
    def "verify jwt cookie authentication works"() {
        given:
        browser.baseUrl = "http://localhost:${embeddedServer.port}"

        when:
        to HomePage

        then:
        at HomePage

        when:
        HomePage homePage = browser.page HomePage

        then:
        homePage.username() == null

        when:
        homePage.login()

        then:
        at LoginPage

        when:
        LoginPage loginPage = browser.page LoginPage
        loginPage.login('foo', 'foo')

        then:
        at LoginPage

        and:
        loginPage.hasErrors()

        when:
        loginPage.login('sherlock', 'password')

        then:
        at HomePage

        when:
        homePage = browser.page HomePage

        then:
        homePage.username() == 'sherlock'

        when:
        homePage.logout()

        then:
        at HomePage

        when:
        homePage = browser.page HomePage

        then:
        homePage.username() == null
    }
}

class LoginForm {
    String username
    String password
}
