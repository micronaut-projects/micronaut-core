package io.micronaut.docs.server.routes

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MyRoutesSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    @AutoCleanup
    HttpClient client = HttpClient.create(embeddedServer.URL)

    void "test custom route"() {
        when:
        String body = client.toBlocking().retrieve("/issues/show/12") // <3>

        then:
        body != null
        body == "Issue # 12!" // <4>
    }
}
