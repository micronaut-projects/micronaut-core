package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JdkBlockingHttpClientBehaviourSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkBlockingHttpClientBehaviourSpec'])

    void "the blocking client refuses a non-blocking thread, as blockFirst did"() {
        given:
        HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

        when:
        Mono.fromCallable { client.toBlocking().retrieve(HttpRequest.GET('/jdk-blocking/text')) }
            .subscribeOn(Schedulers.parallel())
            .block()

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith('block()/blockFirst()/blockLast() are blocking, which is not supported in thread parallel-')

        cleanup:
        client.close()
    }

    void "the handlers set on a blocking client apply to it, and not to the client it was made from"() {
        given:
        DefaultJdkHttpClient client = server.applicationContext.createBean(HttpClient, server.URL) as DefaultJdkHttpClient
        BlockingHttpClient blocking = client.toBlocking()
        def registry = new ContextlessMessageBodyHandlerRegistry(new io.micronaut.runtime.ApplicationConfiguration(), io.micronaut.core.io.buffer.ByteArrayBufferFactory.INSTANCE)
        registry.add(MediaType.of('application/x-marker'), new FixedReader())

        def original = client.handlerRegistry

        when:
        ((JdkBlockingHttpClient) blocking).setMessageBodyHandlerRegistry(registry)

        then:
        blocking.retrieve(HttpRequest.GET('/jdk-blocking/marker'), Marker).value == 'replaced'
        client.handlerRegistry.is(original)
        client.toBlocking().retrieve(HttpRequest.GET('/jdk-blocking/text'), String) == 'text'

        cleanup:
        client.close()
    }

    static class FixedReader implements MessageBodyReader<Marker> {
        @Override
        boolean isReadable(Argument<Marker> type, @Nullable MediaType mediaType) {
            return true
        }

        @Override
        Marker read(Argument<Marker> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            inputStream.readAllBytes()
            return new Marker(value: 'replaced')
        }
    }

    static class Marker {
        String value
    }

    @Requires(property = 'spec.name', value = 'JdkBlockingHttpClientBehaviourSpec')
    @Controller('/jdk-blocking')
    static class TextController {
        @Get(uri = '/text', produces = MediaType.TEXT_PLAIN)
        String text() {
            return 'text'
        }

        @Get(uri = '/marker', produces = 'application/x-marker')
        String marker() {
            return 'marker'
        }
    }
}
