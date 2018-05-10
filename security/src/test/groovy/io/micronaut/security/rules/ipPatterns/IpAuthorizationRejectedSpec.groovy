package io.micronaut.security.rules.ipPatterns

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class IpAuthorizationRejectedSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ipPatterns',
            'micronaut.security.enabled': true,
            'micronaut.security.ipPatterns': ['10.10.0.48', '127.0.0.*']

    ], "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

    void "if you are in the correct ip range, accessing the secured controller with authentication should be successful"() {
        when:
        HttpRequest req = HttpRequest.GET("/secured/authenticated")
                .basicAuth("user", "password")
        client.toBlocking().exchange(req, String)

        then:
        noExceptionThrown()
    }
}
