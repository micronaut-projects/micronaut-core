package io.micronaut.http.server.netty.resources

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

class StreamedFileNotModifiedSpec extends Specification {

    static final long LAST_MODIFIED = 1_700_000_000_000L

    void 'the input stream of a StreamedFile is closed when the response is not modified'() {
        given:
        def context = ApplicationContext.run(['spec.name': 'StreamedFileNotModifiedSpec'])
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URI)
        def controller = context.getBean(StreamedFileController)

        when:
        def ifModifiedSince = ZonedDateTime.ofInstant(Instant.ofEpochMilli(LAST_MODIFIED), ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        def response = client.toBlocking().exchange(HttpRequest.GET('/streamed-file-not-modified')
                .header(HttpHeaders.IF_MODIFIED_SINCE, ifModifiedSince), String)

        then:
        response.status() == HttpStatus.NOT_MODIFIED
        controller.closed.get()

        cleanup:
        client.close()
        server.stop()
        context.close()
    }

    @Requires(property = 'spec.name', value = 'StreamedFileNotModifiedSpec')
    @Controller
    static class StreamedFileController {
        final AtomicBoolean closed = new AtomicBoolean()

        @Get('/streamed-file-not-modified')
        StreamedFile file() {
            def stream = new ByteArrayInputStream('content'.bytes) {
                @Override
                void close() throws IOException {
                    closed.set(true)
                    super.close()
                }
            }
            return new StreamedFile(stream, MediaType.TEXT_PLAIN_TYPE, LAST_MODIFIED)
        }
    }
}
