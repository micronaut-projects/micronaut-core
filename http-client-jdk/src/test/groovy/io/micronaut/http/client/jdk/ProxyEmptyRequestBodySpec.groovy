package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.http.body.stream.AvailableByteArrayBody
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ProxyEmptyRequestBodySpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ProxyEmptyRequestBodySpec'])

    void "the JDK proxy client relays a server request without a body"() {
        expect:
        server.applicationContext.getBean(ProxyHttpClient) instanceof JdkRawHttpClient

        when:
        String response = send("POST /relay HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")

        then:
        response.startsWith("HTTP/1.1 200")
        response.contains("\r\nlength=0\r\n")
    }

    void "the JDK proxy client relays a server request with an empty body"() {
        when:
        String response = send("POST /relay HTTP/1.1\r\nHost: localhost\r\nContent-Type: text/plain\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

        then:
        response.startsWith("HTTP/1.1 200")
        response.contains("\r\nlength=0\r\n")
    }

    void "the JDK raw client sends an empty request body"() {
        given:
        RawHttpClient client = server.applicationContext.createBean(RawHttpClient)

        when:
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) Mono.from(client.exchange(
            HttpRequest.POST(server.URI.toString() + "/length", null).contentType(MediaType.TEXT_PLAIN_TYPE),
            AvailableByteArrayBody.create(ByteArrayBufferFactory.INSTANCE, new byte[0]),
            null,
            RawRequestOptions.proxy()
        )).block()

        then:
        response.code() == 200
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "length=0"

        cleanup:
        response?.close()
        client?.close()
    }

    private String send(String request) {
        new Socket("localhost", server.port).withCloseable { socket ->
            socket.soTimeout = 20_000
            socket.outputStream.write(request.getBytes(StandardCharsets.US_ASCII))
            socket.outputStream.flush()
            return new String(socket.inputStream.readAllBytes(), StandardCharsets.ISO_8859_1)
        }
    }

    @Controller
    @Requires(property = "spec.name", value = "ProxyEmptyRequestBodySpec")
    static class RelayController {
        private final ProxyHttpClient proxyHttpClient
        private final EmbeddedServer embeddedServer

        RelayController(ProxyHttpClient proxyHttpClient, EmbeddedServer embeddedServer) {
            this.proxyHttpClient = proxyHttpClient
            this.embeddedServer = embeddedServer
        }

        @Post(value = "/relay", consumes = MediaType.ALL)
        Publisher<MutableHttpResponse<?>> relay(HttpRequest<?> request) {
            return proxyHttpClient.proxy(request.mutate().uri(URI.create(embeddedServer.URI.toString() + "/length")))
        }

        @Post(value = "/length", consumes = MediaType.ALL, produces = MediaType.TEXT_PLAIN)
        String length(@Nullable @Body String body) {
            return "length=" + (body == null ? 0 : body.length())
        }
    }
}
