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

import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared

class JwtCookieAuthenticationWithPathSpec extends GebSpec {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': 'jwtcookie',
                    'micronaut.http.client.followRedirects': false,
                    'micronaut.security.enabled': true,
                    'micronaut.security.endpoints.login.enabled': true,
                    'micronaut.security.endpoints.logout.enabled': true,
                    // have to set logout path to be under cookie path, otherwise cookie will not be sent to logout controller
                    'micronaut.security.endpoints.logout.path': "/path/to/page/logout",
                    'micronaut.security.token.jwt.enabled': true,
                    'micronaut.security.token.jwt.bearer.enabled': false,
                    'micronaut.security.token.jwt.cookie.enabled': true,
                    'micronaut.security.token.jwt.cookie.cookie-path': "/path/to/page",
                    'micronaut.security.token.jwt.cookie.logout-target-url': "/path/to/page",
                    'micronaut.security.token.jwt.cookie.login-failure-target-url': '/login/authFailed',
                    'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            ], Environment.TEST)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    @Requires({sys['geb.env']})
    def "verify jwt cookie authentication works with configured path"() {
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
        homePage.username() == null

        when:
        to PageOnPath

        then:
        at PageOnPath

        when:
        PageOnPath pageOnPath = browser.page PageOnPath

        then:
        pageOnPath.username() == 'sherlock'

        when:
        pageOnPath.logout()

        then:
        at PageOnPath

        then:
        pageOnPath.username() == null
    }
}

