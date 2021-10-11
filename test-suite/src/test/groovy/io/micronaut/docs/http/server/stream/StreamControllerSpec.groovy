package io.micronaut.docs.http.server.stream

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class StreamControllerSpec extends Specification {

    @Shared @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared @AutoCleanup
    HttpClient client = embeddedServer.getApplicationContext().createBean(HttpClient, embeddedServer.getURL())

    void "test receiving a stream"() {
        when:
        String response = client.toBlocking().retrieve(
                HttpRequest.GET("/stream/write"), String.class)

        then:
        "test" == response
    }

    void "test returning a stream"() {
        when:
        String body = "My body"
        String response = client.toBlocking().retrieve(
                HttpRequest.POST("/stream/read", body)
                        .contentType(MediaType.TEXT_PLAIN_TYPE), String.class)

        then:
        body == response
    }

}
