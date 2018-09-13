package io.micronaut.security.rolesallowed

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RolesAllowedSpec extends Specification {
    static final String SPEC_NAME_PROPERTY = 'spec.name'

    public static final String controllerPath = '/rolesallowed'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY): RolesAllowedSpec.class.simpleName,
            'micronaut.security.enabled': true,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "PermitAllSpec collaborators are loaded"() {
        when:
        embeddedServer.applicationContext.getBean(BookController)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword)

        then:
        noExceptionThrown()
    }

    def "@RolesAllowed(['ROLE_ADMIN', 'ROLE_USER']) annotation is equivalent to @Secured(['ROLE_ADMIN', 'ROLE_USER'])"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/books").basicAuth("user", "password"))

        then:
        noExceptionThrown()
    }

    def "methods in a controller inherit @RolesAllowed at class level"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/classlevel").basicAuth("user", "password"))

        then:
        noExceptionThrown()
    }

    def "@RolesAllowed(['ROLE_ADMIN', 'ROLE_MANAGER']) annotation is equivalent to @Secured(['ROLE_ADMIN', 'ROLE_MANAGER']), if user has only ROLE_USER access is forbidden "() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/forbidenbooks").basicAuth("user", "password"))

        then:
        def e = thrown(HttpClientResponseException)

        e.response.status() == HttpStatus.FORBIDDEN
    }

}
