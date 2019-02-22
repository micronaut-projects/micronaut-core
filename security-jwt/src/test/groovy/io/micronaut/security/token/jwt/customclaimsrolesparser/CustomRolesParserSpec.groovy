package io.micronaut.security.token.jwt.customclaimsrolesparser

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.token.RolesParser
import io.micronaut.security.token.generator.TokenGenerator
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator
import io.micronaut.security.token.jwt.validator.JwtTokenValidator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.ZoneId

class CustomRolesParserSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': "customclaimsrolesparser",
            'micronaut.security.enabled': true,
            'micronaut.security.token.jwt.enabled': true,
            'micronaut.security.token.jwt.signatures.secret.generator.secret': 'pleaseChangeThisSecretForANewOne',
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    @Shared
    Map<String, Object> claims = [
            "jti": "0cacf4f3-757c-49a8-8c4d-d9908146c154",
            "nbf": 0,
            "iat": 1550075809,
            "iss": "https://keycloak.domain.com/auth/realms/master",
            "aud": "client-key",
            "sub": "6285eef9-fc1f-4d82-a0c7-f8edea5b2d86",
            "typ": "Bearer",
            "azp": "client-key",
            "auth_time": 0,
            "session_state": "5ced6a47-2272-4a39-8642-ab16df2c89a0",
            "acr": "1",
            "allowed-origins": [],
            "realm_access": [
                    "roles": [
                            "offline_access",
                            "uma_authorization"
                    ]
            ],
            "resource_access": [
                    "service-name": [
                            "roles": [
                                    "scoped_role_name"
                            ]
                    ],
                    "account": [
                            "roles": [
                                    "manage-account",
                                    "manage-account-links",
                                    "view-profile"
                            ]
                    ]
            ],
            "scope": "email profile",
            "clientId": "client-key",
            "clientHost": "123.123.123.123",
            "email_verified": false,
            "preferred_username": "service-account-client-key",
            "clientAddress": "123.123.123.123",
            "email": "service-account-client-key@placeholder.org"
    ]

    void "verify custom RolesParser can be used"() {
        expect:
        embeddedServer.applicationContext.containsBean(CustomRolesParser)
        embeddedServer.applicationContext.containsBean(ControllerWithRealmAccessRole)

        when:
        RolesParser rolesParser = embeddedServer.applicationContext.getBean(RolesParser)

        then:
        noExceptionThrown()
        rolesParser instanceof CustomRolesParser

        when:
        TokenGenerator tokenGenerator = embeddedServer.applicationContext.getBean(JwtTokenGenerator)

        then:
        noExceptionThrown()

        when: 'generate a JWT with an expiration date in the future'
        LocalDateTime time = LocalDateTime.now()
        time = time.plusDays(1)
        ZoneId zoneId = ZoneId.systemDefault()
        long expiration = time.atZone(zoneId).toEpochSecond()
        claims.exp = expiration
        Optional<String> jwt = tokenGenerator.generateToken(claims)

        then:
        jwt.isPresent()

        when:
        HttpResponse response = client.exchange(HttpRequest.GET('/').bearerAuth(jwt.get()))

        then:
        response.status() == HttpStatus.OK
    }

}
