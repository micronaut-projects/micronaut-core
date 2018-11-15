package io.micronaut.docs.server.intro

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HelloControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer,
                    ['spec.name': HelloControllerSpec.simpleName],
                     Environment.TEST)

    @Shared @AutoCleanup HttpClient client = HttpClient.create(embeddedServer.URL)

    void "test hello world response"() {
        expect:
        client.toBlocking()
              .retrieve(HttpRequest.GET('/hello')) == "Hello World"
    }
}
