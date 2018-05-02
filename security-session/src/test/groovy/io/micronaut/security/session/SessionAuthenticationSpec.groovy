package io.micronaut.security.session

import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared

class SessionAuthenticationSpec extends GebSpec {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            [
                    'spec.name': 'securitysession',
                    'micronaut.security.enabled': true,
                    'micronaut.security.endpoints.login': true,
                    'micronaut.security.endpoints.logout': true,
                    'micronaut.security.session.enabled': true,
                    'micronaut.security.session.loginFailureTargetUrl': '/login/authFailed',
            ], 'test')

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    def "verify session based authentication works"() {
        given:
        context.getBean(HomeController.class)
        context.getBean(LoginAuthController.class)
        context.getBean(AuthenticationProviderUserPassword.class)
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
