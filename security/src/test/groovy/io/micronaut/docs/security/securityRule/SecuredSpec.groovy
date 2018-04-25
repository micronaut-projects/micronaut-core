package io.micronaut.docs.security.securityRule

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.YamlAsciidocTagCleaner
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.authorization.AuthorizationUtils
import io.micronaut.security.endpoints.LoginController
import org.yaml.snakeyaml.Yaml
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SecuredSpec extends Specification implements AuthorizationUtils {

    @Shared
    Map<String, Object> config = [
            'spec.authentication': true,
            "micronaut.security.enabled": true,
            "micronaut.security.endpoints.login": true,
            "micronaut.security.token.bearer.enabled": true,
            "micronaut.security.jwt.enabled": true,
            "micronaut.security.jwt.generator.signature.enabled": true,
            "micronaut.security.jwt.generator.signature.secret": 'qrD6h8K6S9503Q06Y6Rfk21TErImPYqa',
    ]

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, config as Map<String, Object>, "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "verify you can access an endpoint annotated with @Secured('isAnonymous()') without authentication"() {
        when:
        get("/example/anonymous")

        then:
        noExceptionThrown()
    }

    void "verify you can access an endpoint annotated with @Secured('isAuthenticated()') with an authenticated user"() {
        given:
        embeddedServer.applicationContext.getBean(LoginController.class)

        when:
        String token = loginWith("valid")

        then:
        token

        when:
        get("/example/authenticated")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        get("/example/authenticated", token)

        then:
        noExceptionThrown()
    }

    void "verify you can access an endpoint annotated with @Secured([\"ROLE_ADMIN\", \"ROLE_X\"]) with an authenticated user with one of those roles"() {
        when:
        get("/example/admin")

        then:
        HttpClientResponseException e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNAUTHORIZED

        when:
        String token = loginWith("valid")

        then:
        token

        when:
        get("/example/admin", token)

        then:
        e = thrown(HttpClientResponseException)
        e.status == HttpStatus.FORBIDDEN

        when:
        def admintoken = loginWith("admin")

        then:
        admintoken
        token != admintoken

        when:
        get("/example/admin", admintoken)

        then:
        noExceptionThrown()
    }
}