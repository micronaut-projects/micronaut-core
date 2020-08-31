package io.micronaut.docs.http.client.bind

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CustomBinderSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer)

    void "test binding to the request"() {
        ClientBindClient client = server.getApplicationContext().getBean(ClientBindClient)

        expect:
        client.get(new Metadata(3.6, 42)) == "3.6"
    }
}
