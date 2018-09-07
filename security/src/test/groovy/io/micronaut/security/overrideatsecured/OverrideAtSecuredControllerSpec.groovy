package io.micronaut.security.overrideatsecured

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OverrideAtSecuredControllerSpec extends Specification {
    static final String SPEC_NAME_PROPERTY = 'spec.name'
    static  final String controllerPath = "/overrideatsecured"

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY): OverrideAtSecuredControllerSpec.class.simpleName,
            "micronaut.security.interceptUrlMap": [
                    [pattern: "${controllerPath}/books", access: ['isAuthenticated()']]
            ],
            'micronaut.security.enabled': true,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "OverrideAtSecuredControllerSpec collaborators are loaded"() {
        when:
        embeddedServer.applicationContext.getBean(BookController)

        then:
        noExceptionThrown()

        when:
        embeddedServer.applicationContext.getBean(AuthenticationProviderUserPassword)

        then:
        noExceptionThrown()
    }

    def "user can fetch books because interceptUrlMap overrides controller restrictive @Secured(ROLE_ADMIN)"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/books")
                .basicAuth("user", "password"))

        then:
        noExceptionThrown()
    }

}
