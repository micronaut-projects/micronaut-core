package io.micronaut.http.server.netty.resources

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.atomic.AtomicBoolean

class HeadClosesStreamedBodySpec extends Specification {

    void 'a HEAD request closes the body of #path'() {
        given:
        def context = ApplicationContext.run(['spec.name': 'HeadClosesStreamedBodySpec'])
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URI)
        def controller = context.getBean(HeadBodyController)

        when:
        def response = client.toBlocking().exchange(HttpRequest.HEAD(path))

        then:
        response.status() == HttpStatus.OK
        new PollingConditions(timeout: 5).eventually {
            assert controller.closed.get()
        }

        cleanup:
        client.close()
        server.stop()
        context.close()

        where:
        path << ['/head-body/streamed-file', '/head-body/input-stream']
    }

    @Requires(property = 'spec.name', value = 'HeadClosesStreamedBodySpec')
    @Controller('/head-body')
    static class HeadBodyController {
        final AtomicBoolean closed = new AtomicBoolean()

        private InputStream stream() {
            return new ByteArrayInputStream('content'.bytes) {
                @Override
                void close() throws IOException {
                    closed.set(true)
                    super.close()
                }
            }
        }

        @Get('/streamed-file')
        StreamedFile file() {
            return new StreamedFile(stream(), MediaType.TEXT_PLAIN_TYPE)
        }

        @Get(value = '/input-stream', produces = MediaType.TEXT_PLAIN)
        InputStream inputStream() {
            return stream()
        }
    }
}
