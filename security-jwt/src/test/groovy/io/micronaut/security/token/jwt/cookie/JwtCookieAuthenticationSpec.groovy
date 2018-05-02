package io.micronaut.security.token.jwt.cookie

import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.endpoints.LoginController
import io.micronaut.security.endpoints.LogoutController
import io.micronaut.security.token.jwt.bearer.BearerTokenReader
import io.micronaut.security.token.jwt.encryption.EncryptionConfiguration
import io.micronaut.security.token.jwt.signature.SignatureConfiguration
import spock.lang.AutoCleanup
import spock.lang.Shared

class JwtCookieAuthenticationSpec extends GebSpec {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': 'jwtcookie',
                    'micronaut.security.enabled': true,
                    'micronaut.security.endpoints.login': true,
                    'micronaut.security.endpoints.logout': true,
                    'micronaut.security.token.jwt.enabled': true,
                    'micronaut.security.token.jwt.bearer.enabled': false,
                    'micronaut.security.token.jwt.cookie.enabled': true,
                    'micronaut.security.token.jwt.cookie.loginFailureTargetUrl': '/login/authFailed',
                    'micronaut.security.token.jwt.signatures.secret.generator.secret': 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
            ], 'test')

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    def "verify session based authentication works"() {
        given:
        browser.baseUrl = "http://localhost:${embeddedServer.port}"
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
