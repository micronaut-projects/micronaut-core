package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ReadBuffer
import io.micronaut.core.io.buffer.ReadBufferFactory
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.body.ByteBodyFactory
import io.micronaut.http.client.AsyncRawHttpClient
import io.micronaut.http.client.RawRequestOptions
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * The response timeout of a raw exchange of the JDK client starts once the body of the request
 * is sent: the time of a slow upload does not count.
 */
class JdkRawResponseTimeoutAfterUploadSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                    : 'JdkRawResponseTimeoutAfterUploadSpec',
            'micronaut.server.idle-timeout': '5s',
    ])

    private static ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE)

    private static slowBody(int parts) {
        factory.adapt(Flux.range(0, parts)
                .delayElements(Duration.ofMillis(100))
                .map(i -> (ReadBuffer) ReadBufferFactory.getJdkFactory().copyOf("x", StandardCharsets.UTF_8)))
    }

    private exchange(long delay, int parts) {
        AsyncRawHttpClient client = server.applicationContext.getBean(AsyncRawHttpClient)
        def request = HttpRequest.create(HttpMethod.POST, server.URI.toString() + "/jdk-after-upload/count?delay=" + delay)
                .contentType(MediaType.TEXT_PLAIN_TYPE)
        client.exchange(request, slowBody(parts), RawRequestOptions.builder().responseTimeout(Duration.ofMillis(500)).build())
                .toCompletableFuture().get(10, TimeUnit.SECONDS)
    }

    void "a slow upload does not count towards the response timeout"() {
        when:
        ByteBodyHttpResponse<?> response = (ByteBodyHttpResponse<?>) exchange(0, 10)

        then:
        response.code() == 200
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == "10"

        cleanup:
        response?.close()
    }

    void "a slow response after the upload still times out"() {
        when:
        exchange(3000, 2)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof ReadTimeoutException
    }

    @Controller("/jdk-after-upload")
    @Requires(property = "spec.name", value = "JdkRawResponseTimeoutAfterUploadSpec")
    static class CountController {
        @Post(value = "/count", consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        Mono<String> count(@QueryValue long delay, @Body String body) {
            Mono.delay(Duration.ofMillis(delay)).thenReturn(String.valueOf(body.length()))
        }
    }
}
