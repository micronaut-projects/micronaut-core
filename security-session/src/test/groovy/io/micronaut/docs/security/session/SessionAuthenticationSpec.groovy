package io.micronaut.docs.security.session

import geb.spock.GebSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.runtime.server.EmbeddedServer
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared

class SessionAuthenticationSpec extends GebSpec implements YamlAsciidocTagCleaner {

    String yamlConfig = '''\
//tag::yamlconfig[]
micronaut:
  security:
    enabled: true
    endpoints:
      login: true
      logout: true
    session:
      enabled: true
      loginFailureTargetUrl: /login/authFailed
'''//end::yamlconfig[]

    @Shared
    Map<String, Object> configMap = ['micronaut': [
            'security': [
                    'enabled': true,
                    'endpoints': [
                            'login': true,
                            'logout': true,
                    ],
                    'session': [
                            'enabled': true,
                            'loginFailureTargetUrl': '/login/authFailed',
                    ]
            ]
        ]
    ]

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run([
                    'spec.name': 'securitysession'
            ] << flatten(configMap), 'test')

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
        Map m = new Yaml().load(cleanYamlAsciidocTag(yamlConfig))

        then:
        m == configMap

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
