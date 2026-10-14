package io.micronaut.http.server.netty.types

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.http.server.types.files.SystemFile
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.ZonedDateTime
import java.util.concurrent.CopyOnWriteArrayList

import static io.micronaut.http.HttpHeaders.ACCEPT_RANGES
import static io.micronaut.http.HttpHeaders.CACHE_CONTROL
import static io.micronaut.http.HttpHeaders.CONTENT_DISPOSITION
import static io.micronaut.http.HttpHeaders.CONTENT_LENGTH
import static io.micronaut.http.HttpHeaders.CONTENT_TYPE
import static io.micronaut.http.HttpHeaders.IF_MODIFIED_SINCE
import static io.micronaut.http.HttpHeaders.LAST_MODIFIED

/**
 * A HEAD request answers the headers a GET request would, without the content (RFC 9110, section 9.3.2),
 * and closes the file a route returned for it.
 */
class HeadFileResponseSpec extends Specification {

    static final String CONTENT = 'The content of the file'
    static final long LAST_MODIFIED_TIME = ZonedDateTime.parse('2024-01-02T03:04:05Z').toInstant().toEpochMilli()
    static final List<String> REPRESENTATION_HEADERS = [CONTENT_TYPE, CONTENT_LENGTH, LAST_MODIFIED, CACHE_CONTROL, CONTENT_DISPOSITION, ACCEPT_RANGES]
    static final List<RecordingInputStream> STREAMS = new CopyOnWriteArrayList<>()
    static File file

    @Shared
    @AutoCleanup
    EmbeddedServer server

    @Shared
    @AutoCleanup
    HttpClient client

    PollingConditions conditions = new PollingConditions(timeout: 5)

    void setupSpec() {
        file = Files.createTempFile('head-file-response', '.txt').toFile()
        file.text = CONTENT
        file.lastModified = LAST_MODIFIED_TIME
        file.deleteOnExit()
        server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                      : 'HeadFileResponseSpec',
                'micronaut.router.static-resources.default.paths': ['classpath:public'],
                'micronaut.router.static-resources.default.mapping': '/static/**',
        ])
        client = server.applicationContext.createBean(HttpClient, server.URI)
    }

    void setup() {
        STREAMS.clear()
    }

    void 'a HEAD request closes a streamed file with the headers of a GET request: #uri'() {
        when:
        HttpResponse<String> get = client.toBlocking().exchange(HttpRequest.GET(uri), String)

        then:
        get.status == HttpStatus.OK
        get.body() == CONTENT
        conditions.eventually {
            assert STREAMS.size() == 1
            assert STREAMS[0].closed
        }

        when:
        STREAMS.clear()
        HttpResponse<?> head = client.toBlocking().exchange(HttpRequest.HEAD(uri))

        then:
        head.status == HttpStatus.OK
        !head.body.present
        conditions.eventually {
            assert STREAMS.size() == 1
            assert STREAMS[0].closed
        }
        head.header(CONTENT_TYPE) == expectedContentType
        sameRepresentationHeaders(get, head)

        where:
        uri                              | expectedContentType
        '/head-file/streamed'            | MediaType.TEXT_PLAIN
        '/head-file/streamed-length'     | MediaType.TEXT_PLAIN
        '/head-file/streamed-attachment' | MediaType.TEXT_PLAIN
        '/head-file/input-stream'        | MediaType.TEXT_PLAIN
    }

    void 'a HEAD request answers the headers of a GET request for a system file: #uri'() {
        when:
        HttpResponse<String> get = client.toBlocking().exchange(HttpRequest.GET(uri), String)
        HttpResponse<?> head = client.toBlocking().exchange(HttpRequest.HEAD(uri))

        then:
        get.status == HttpStatus.OK
        get.body() == body
        head.status == HttpStatus.OK
        !head.body.present
        head.header(CONTENT_TYPE) == expectedContentType
        head.header(CONTENT_LENGTH) == String.valueOf(body.getBytes(StandardCharsets.UTF_8).length)
        sameRepresentationHeaders(get, head)

        where:
        uri                         | body                                                        | expectedContentType
        '/head-file/system'         | CONTENT                                                     | MediaType.TEXT_PLAIN
        '/static/index.html'        | HeadFileResponseSpec.getResource('/public/index.html').text | MediaType.TEXT_HTML
    }

    void 'a conditional HEAD request is not modified like a GET request'() {
        given:
        String lastModified = client.toBlocking().exchange(HttpRequest.GET('/head-file/system'), String).header(LAST_MODIFIED)

        when:
        HttpResponse<?> get = client.toBlocking().exchange(HttpRequest.GET('/head-file/streamed').header(IF_MODIFIED_SINCE, lastModified))
        HttpResponse<?> head = client.toBlocking().exchange(HttpRequest.HEAD('/head-file/streamed').header(IF_MODIFIED_SINCE, lastModified))

        then:
        get.status == HttpStatus.NOT_MODIFIED
        head.status == HttpStatus.NOT_MODIFIED
    }

    private static boolean sameRepresentationHeaders(HttpResponse<?> get, HttpResponse<?> head) {
        for (String name : REPRESENTATION_HEADERS) {
            assert head.header(name) == get.header(name): name
        }
        return true
    }

    static RecordingInputStream record() {
        RecordingInputStream stream = new RecordingInputStream(CONTENT.getBytes(StandardCharsets.UTF_8))
        STREAMS.add(stream)
        return stream
    }

    static class RecordingInputStream extends ByteArrayInputStream {
        volatile boolean closed

        RecordingInputStream(byte[] content) {
            super(content)
        }

        @Override
        void close() throws IOException {
            closed = true
            super.close()
        }
    }

    @Requires(property = 'spec.name', value = 'HeadFileResponseSpec')
    @Controller('/head-file')
    static class HeadFileController {

        @Get('/streamed')
        StreamedFile streamed() {
            new StreamedFile(record(), MediaType.TEXT_PLAIN_TYPE, LAST_MODIFIED_TIME)
        }

        @Get('/streamed-length')
        StreamedFile streamedLength() {
            new StreamedFile(record(), MediaType.TEXT_PLAIN_TYPE, LAST_MODIFIED_TIME, CONTENT.length())
        }

        @Get('/streamed-attachment')
        StreamedFile streamedAttachment() {
            new StreamedFile(record(), MediaType.TEXT_PLAIN_TYPE, LAST_MODIFIED_TIME, CONTENT.length()).attach('content.txt')
        }

        @Get(value = '/input-stream', produces = MediaType.TEXT_PLAIN)
        InputStream inputStream() {
            record()
        }

        @Get('/system')
        SystemFile system() {
            new SystemFile(file)
        }
    }
}
