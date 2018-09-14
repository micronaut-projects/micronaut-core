package io.micronaut.security.permitalloverridesrolesallowed

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PermitAllOverridesRolesAllowedSpec extends Specification {
    static final String SPEC_NAME_PROPERTY = 'spec.name'

    public static final String controllerPath = '/permitalloverridesrolesallowed'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            (SPEC_NAME_PROPERTY): PermitAllOverridesRolesAllowedSpec.class.simpleName,
            'micronaut.security.enabled': true,
    ], Environment.TEST)

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "PermitAllOverridesRolesAllowedSpec collaborators are loaded"() {
        when:
        embeddedServer.applicationContext.getBean(BookController)

        then:
        noExceptionThrown()
    }

    def " If the RolesAllowed is specified at the class level and PermitAll annotation is applied at the method level, the PermitAll annotation overrides the RolesAllowed for the specified method."() {
        when:
        client.toBlocking().exchange(HttpRequest.GET("${controllerPath}/books"))

        then:
        noExceptionThrown()
    }
}
