package io.micronaut.docs.rejection

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.jwt.AuthorizationUtils
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class RejectionHandlerOverrideSpec extends Specification implements AuthorizationUtils {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
        [
            'spec.name': 'rejection-handler',
            'micronaut.security.enabled': true,
            'micronaut.security.endpoints.login.enabled': true,
        ], Environment.TEST)

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())


    void "test the rejection handler can be overridden"() {
        when:
        client.toBlocking().exchange("/rejection-handler")

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.header("X-Reason") == "Example Header"
    }


    @Controller("/rejection-handler")
    @Requires(property = "spec.name", value = "rejection-handler")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    static class SecuredResource {

        @Get
        String foo() {
            ""
        }

    }
}
