package io.micronaut.docs.server.body

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MessageControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)
    @Shared @AutoCleanup HttpClient httpClient =
            embeddedServer.getApplicationContext()
                          .createBean(HttpClient, embeddedServer.getURL())

    void "test echo response"() {
        given:
        String body = "My Text"
        String response = httpClient.toBlocking().retrieve(
                HttpRequest.POST('/receive/echo', body)
                           .contentType(MediaType.TEXT_PLAIN_TYPE),
                String
        )

        expect:
        response == body
    }

    void "test echo reactive response"() {
        given:
        String body = "My Text"
        String response = httpClient.toBlocking().retrieve(
                HttpRequest.POST('/receive/echo-publisher', body)
                        .contentType(MediaType.TEXT_PLAIN_TYPE),
                String
        )
        expect:
        response == body
    }

}
