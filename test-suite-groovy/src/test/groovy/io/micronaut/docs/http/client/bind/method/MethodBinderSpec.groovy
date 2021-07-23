package io.micronaut.docs.http.client.bind.method

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MethodBinderSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

    void "text binding to the request"() {
        when:
        NameAuthorizedClient client = server.getApplicationContext().getBean(NameAuthorizedClient.class)

        then:
        client.get() == "Hello, Bob"
    }
}
