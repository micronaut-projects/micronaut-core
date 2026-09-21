package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.core.type.MutableHeaders
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.body.MessageBodyWriter
import io.micronaut.http.client.HttpClient
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import org.jspecify.annotations.NonNull
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class ExplicitBodyWriterSpec extends Specification {

    def 'explicit body writer is used for a String body'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ExplicitBodyWriterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        expect:
        client.retrieve('/explicit-writer/string') == 'custom writer'

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    def 'explicit body writer is used for a non-String body'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ExplicitBodyWriterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        expect:
        client.retrieve('/explicit-writer/payload') == 'custom payload'

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    def 'route body writer is still used when the response has no explicit writer'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ExplicitBodyWriterSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        expect:
        client.retrieve('/explicit-writer/plain-string') == 'plain body'

        cleanup:
        client.close()
        server.stop()
        ctx.close()
    }

    static class Payload {
        final String name

        Payload(String name) {
            this.name = name
        }
    }

    static class CustomStringWriter implements MessageBodyWriter<String> {
        @Override
        void writeTo(@NonNull Argument<String> type,
                     @NonNull MediaType mediaType,
                     String object,
                     @NonNull MutableHeaders outgoingHeaders,
                     @NonNull OutputStream outputStream) throws CodecException {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
            try {
                outputStream.write('custom writer'.getBytes(StandardCharsets.UTF_8))
            } catch (IOException e) {
                throw new CodecException('Error writing custom string body', e)
            }
        }
    }

    static class CustomPayloadWriter implements MessageBodyWriter<Payload> {
        @Override
        void writeTo(@NonNull Argument<Payload> type,
                     @NonNull MediaType mediaType,
                     Payload object,
                     @NonNull MutableHeaders outgoingHeaders,
                     @NonNull OutputStream outputStream) throws CodecException {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN)
            try {
                outputStream.write('custom payload'.getBytes(StandardCharsets.UTF_8))
            } catch (IOException e) {
                throw new CodecException('Error writing custom payload body', e)
            }
        }
    }

    @Controller('/explicit-writer')
    @Requires(property = 'spec.name', value = 'ExplicitBodyWriterSpec')
    static class ExplicitWriterController {
        @Get('/string')
        HttpResponse<String> string() {
            MutableHttpResponse<String> response = HttpResponse.ok('should be ignored')
            response.bodyWriter(new CustomStringWriter())
            return response
        }

        @Get('/payload')
        HttpResponse<Payload> payload() {
            MutableHttpResponse<Payload> response = HttpResponse.ok(new Payload('ignored'))
            response.bodyWriter(new CustomPayloadWriter())
            return response
        }

        @Get('/plain-string')
        HttpResponse<String> plainString() {
            return HttpResponse.ok('plain body')
        }
    }
}
