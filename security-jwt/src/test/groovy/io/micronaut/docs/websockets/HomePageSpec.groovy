package io.micronaut.docs.websockets

import geb.Browser
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.generator.TokenGenerator
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.LocalDateTime
import java.time.ZoneId

class HomePageSpec extends Specification {

    @Shared
    Map<String, Object> conf = [
            'spec.name': 'websockets',
            'micronaut.security.enabled': true,
            'micronaut.security.intercept-url-map': [
                    [
                            pattern: '/assets/*',
                            ('http-method'): 'GET',
                            'access': ['isAnonymous()']
                    ]
            ],
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.secret.generator.secret': 'pleaseChangeThisSecretForANewOne',
            'micronaut.router.static-resources.default.enabled': true,
            'micronaut.router.static-resources.default.mapping': '/assets/**',
            'micronaut.router.static-resources.default.paths': ['classpath:websockets'],
    ]

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, conf)

    Optional<String> generateJwt(TokenGenerator tokenGenerator) {
        LocalDateTime time = LocalDateTime.now()
        time = time.plusDays(1)
        ZoneId zoneId = ZoneId.systemDefault()
        long expiration = time.atZone(zoneId).toEpochSecond()
        Map<String, Object> claims = [sub: 'john']
        claims.exp = expiration

        tokenGenerator.generateToken(claims)
    }

    @Requires({sys['geb.env']})
    def "check websocket connects"() {
        given:
        Browser browser = new Browser()
        browser.baseUrl = embeddedServer.URL.toString()

        expect:
        embeddedServer.applicationContext.containsBean(MockAuthenticationProvider)
        embeddedServer.applicationContext.containsBean(ParamTokenReader)

        when:
        TokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator)

        then:
        noExceptionThrown()

        when:
        Optional<String> accessToken = generateJwt(tokenGenerator)

        then:
        accessToken.isPresent()

        when:
        WebSocketsHomePage homePage = browser.to(WebSocketsHomePage, accessToken.get())

        then:
        browser.at(WebSocketsHomePage)

        and:
        new PollingConditions().eventually {
            homePage.receivedMessages() == ['joined!']
            !homePage.sentMessages()
        }

        when:
        homePage.send('Hello')

        then:
        new PollingConditions().eventually {
            homePage.receivedMessages() == ['joined!', 'Hello']
            homePage.sentMessages() == ['Hello']
        }

        when:
        homePage.close()

        then:
        homePage.status().contains('Disconnected')
    }
}
