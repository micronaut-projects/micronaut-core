package io.micronaut.http.server.netty.http2

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpVersion
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.netty.handler.codec.compression.DecompressionException
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.zip.GZIPOutputStream

/**
 * A request body that is declared as gzip but is not must reach the application exception
 * handler over HTTP/2, like over HTTP/1.
 */
@MicronautTest
@Property(name = "micronaut.server.http-version", value = "2.0")
@Property(name = "micronaut.http.client.plaintext-mode", value = "h2c_prior_knowledge")
@Property(name = "micronaut.server.ssl.enabled", value = "false")
@Property(name = "spec.name", value = "H2cInvalidContentEncodingSpec")
class H2cInvalidContentEncodingSpec extends Specification {
    @Inject
    EmbeddedServer embeddedServer

    @Inject
    HttpClient httpClient

    def 'invalid gzip body is answered by the exception handler'() {
        when:
        httpClient.toBlocking().exchange(HttpRequest.POST("http://localhost:${embeddedServer.port}/h2c-invalid-gzip", '{"test":"invalid-gzip"}')
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Encoding", "gzip"), String)

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST
        e.response.getBody(String).get() == 'invalid content encoding HTTP_2_0'
    }

    def 'valid gzip body is decompressed'() {
        given:
        def bos = new ByteArrayOutputStream()
        new GZIPOutputStream(bos).withCloseable { it.write('{"test":"ok"}'.bytes) }

        when:
        def response = httpClient.toBlocking().exchange(HttpRequest.POST("http://localhost:${embeddedServer.port}/h2c-invalid-gzip", bos.toByteArray())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Encoding", "gzip"), String)

        then:
        response.body() == '{"test":"ok"}'
    }

    @Requires(property = "spec.name", value = "H2cInvalidContentEncodingSpec")
    @Controller("/h2c-invalid-gzip")
    static class GzipController {
        @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.TEXT_PLAIN)
        String echo(@Body String body) {
            return body
        }
    }

    @Requires(property = "spec.name", value = "H2cInvalidContentEncodingSpec")
    @Produces
    @Singleton
    static class InvalidGzipHandler implements ExceptionHandler<DecompressionException, HttpResponse<?>> {
        @Override
        HttpResponse<?> handle(HttpRequest request, DecompressionException exception) {
            return HttpResponse.badRequest("invalid content encoding " + request.httpVersion)
                    .contentType(MediaType.TEXT_PLAIN)
        }
    }
}
