package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The mutable copy of a JDK client response keeps the received body bytes.
 */
class JdkMutableResponseSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkMutableResponseSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "the mutable copy of an error response keeps the error body"() {
        when:
        client.toBlocking().exchange(HttpRequest.GET('/jdk-mutable-response/missing'), Map)

        then:
        HttpClientResponseException e = thrown()
        MutableHttpResponse<?> mutable = e.response.toMutableResponse()
        mutable.status == HttpStatus.NOT_FOUND
        new String(mutable.getBody(byte[]).get(), StandardCharsets.UTF_8) == '{"message":"not here"}'
        mutable.getBody(Map).get() == [message: 'not here']

        cleanup:
        if (mutable instanceof ByteBodyHttpResponse) {
            mutable.close()
        }
    }

    void "the mutable copy of a success response keeps the converted body and the bytes"() {
        when:
        MutableHttpResponse<?> mutable = client.toBlocking().exchange(HttpRequest.GET('/jdk-mutable-response/found'), Map).toMutableResponse()

        then:
        mutable.body() == [name: 'found']
        new String(mutable.getBody(byte[]).get(), StandardCharsets.UTF_8) == '{"name":"found"}'

        cleanup:
        if (mutable instanceof ByteBodyHttpResponse) {
            mutable.close()
        }
    }

    @Requires(property = 'spec.name', value = 'JdkMutableResponseSpec')
    @Controller('/jdk-mutable-response')
    static class MutableResponseController {

        @Get('/missing')
        HttpResponse<String> missing() {
            HttpResponse.<String> notFound().contentType(MediaType.APPLICATION_JSON_TYPE).body('{"message":"not here"}')
        }

        @Get('/found')
        HttpResponse<String> found() {
            HttpResponse.ok('{"name":"found"}').contentType(MediaType.APPLICATION_JSON_TYPE)
        }
    }
}
