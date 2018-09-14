package io.micronaut.security.utils

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class SecurityServiceSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'
    static  final String controllerPath = "/securityutils"

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY): SecurityServiceSpec.class.simpleName,
            'micronaut.security.enabled': true,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "SecurityServiceSpec collaborators are loaded"() {
        when:
        embeddedServer.applicationContext.getBean(SecurityServiceController)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword)

        then:
        noExceptionThrown()
    }

    void "verify SecurityService.isAuthenticated()"() {
        when:
        Boolean authenticated = client.toBlocking().retrieve(HttpRequest.GET("${controllerPath}/authenticated").basicAuth("user", "password"), Boolean)

        then:
        authenticated

        when:
        authenticated = client.toBlocking().retrieve(HttpRequest.GET("${controllerPath}/authenticated"), Boolean)

        then:
        !authenticated
    }

    void "verify SecurityService.isCurrentUserInRole()"() {
        when:
        HttpRequest request = HttpRequest.GET("${controllerPath}/roles?role=ROLE_USER")
                .basicAuth("user", "password")
        Boolean hasRole = client.toBlocking().retrieve(request, Boolean)

        then:
        hasRole

        when:
        request = HttpRequest.GET("${controllerPath}/roles?role=ROLE_ADMIN")
                .basicAuth("user", "password")
        hasRole = client.toBlocking().retrieve(request, Boolean)

        then:
        !hasRole

        when:
        request = HttpRequest.GET("${controllerPath}/roles?role=ROLE_USER")
        hasRole = client.toBlocking().retrieve(request, Boolean)

        then:
        !hasRole
    }

    void "verify SecurityService.currentUserLogin()"() {
        when:
        String username = client.toBlocking().retrieve(HttpRequest.GET("${controllerPath}/currentuser").basicAuth("user", "password"), String)

        then:
        username == "user"

        when:
        username = client.toBlocking().retrieve(HttpRequest.GET("${controllerPath}/currentuser"), String)

        then:
        username == "Anonymous"
    }
}
