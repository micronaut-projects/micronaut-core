package io.micronaut.docs.websockets


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

class EchoClientWebSocketSpec extends Specification {

    @Shared
    Map<String, Object> conf = [
            'spec.name': 'websockets',
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

    def "check websocket connects"() {

        when:
        TokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator)

        then:
        noExceptionThrown()

        when:
        Optional<String> accessToken = generateJwt(tokenGenerator)

        then:
        accessToken.isPresent()

        when:
        String token = accessToken.get()
        HttpRequest request = HttpRequest.GET("/echo").bearerAuth(token)

        EchoClientWebSocket echoClientWebSocket = wsClient.connect(EchoClientWebSocket, request).blockingFirst()

        then:
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == ['joined!']
        }

        when:
        echoClientWebSocket.send('Hello')

        then:
        new PollingConditions().eventually {
            echoClientWebSocket.receivedMessages() == ['joined!', 'Hello']
        }

        cleanup:
        echoClientWebSocket.close()
    }
}
