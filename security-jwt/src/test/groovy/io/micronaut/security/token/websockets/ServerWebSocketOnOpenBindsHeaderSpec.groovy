package io.micronaut.security.token.websockets

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.generator.TokenGenerator
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.websocket.RxWebSocketClient
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.time.LocalDateTime
import java.time.ZoneId

class ServerWebSocketOnOpenBindsHeaderSpec extends Specification {

    @Shared
    Map<String, Object> conf = [
            'spec.name': 'websockets-on-open-header',
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.secret.generator.secret': 'pleaseChangeThisSecretForANewOne',
    ]

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, conf)

    @Shared
    @AutoCleanup
    RxWebSocketClient wsClient = embeddedServer.applicationContext.createBean(RxWebSocketClient, embeddedServer.URL)

    Optional<String> generateJwt(TokenGenerator tokenGenerator) {
        LocalDateTime time = LocalDateTime.now()
        time = time.plusDays(1)
        ZoneId zoneId = ZoneId.systemDefault()
        long expiration = time.atZone(zoneId).toEpochSecond()
        Map<String, Object> claims = [sub: 'john']
        claims.exp = expiration

        tokenGenerator.generateToken(claims)
    }

    def "ServerWebSocket OnOpen method can bind a @Header"() {

        when:
        TokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator)

        then:
        noExceptionThrown()

        when:
        Optional<String> accessToken = generateJwt(tokenGenerator)

        then:
        accessToken.isPresent()

        when: 'connecting with a Authorization Header with value Bearer XXX'
        String token = accessToken.get()
        HttpRequest request = HttpRequest.GET("/echo").bearerAuth(token)
        EchoClientWebSocket echoClientWebSocket = wsClient.connect(EchoClientWebSocket, request).blockingFirst()

        then:
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages().any { it.contains 'joined!' }
        }

        and: 'the response from the web socket server if the header is present in onOpen contains " with ..."'
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages().any { it.contains 'joined! with Bearer ' + token }
        }

        cleanup:
        echoClientWebSocket.close()
    }
}
