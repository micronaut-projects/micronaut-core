package io.micronaut.docs.server.filters.bodybuffer

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.security.MessageDigest

class ChecksumFilterSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer, ['spec.name': 'ChecksumFilterSpec'])
    @Shared @AutoCleanup HttpClient client =
            embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    void "the filter reads the body and the controller reads it again"() {
        given:
        String body = 'x' * (256 * 1024)

        when:
        String response = client.toBlocking().retrieve(HttpRequest.POST('/checksum', body)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header('X-Content-SHA256', MessageDigest.getInstance('SHA-256').digest(body.bytes).encodeHex().toString()))

        then:
        response == "Received ${body.length()} characters"
    }

    void "a mismatch is rejected"() {
        when:
        client.toBlocking().retrieve(HttpRequest.POST('/checksum', 'hello')
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .header('X-Content-SHA256', 'wrong'))

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
    }
}
