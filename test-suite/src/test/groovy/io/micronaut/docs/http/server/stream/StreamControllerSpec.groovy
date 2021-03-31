package io.micronaut.docs.http.server.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class StreamControllerSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())

    void "test receiving and returning a stream"() {

    }

}
