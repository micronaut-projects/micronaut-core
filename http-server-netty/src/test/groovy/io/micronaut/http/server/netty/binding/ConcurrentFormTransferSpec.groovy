package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Timeout

import java.time.Duration
import java.util.function.Supplier

@Requires({ jvm.current.isJava11Compatible() }) // java.net.http.HttpClient
class ConcurrentFormTransferSpec extends Specification {
    private Class<?> loadClass(String clientName) {
        getClass().classLoader.loadClass(clientName)
    }

    def boundaryString = '----*+*+*+*+*+*+*+*+*+*+'

    /**
     * Initial part of file upload field
     */
    InputStream uploadField(String name, String filename, String contentType) {
        def field = """\r
--${boundaryString}\r
Content-Disposition: form-data; name="${name}"; filename="${filename}"\r
Content-Type: ${contentType}\r
\r
"""
        new ByteArrayInputStream(field.bytes)
    }

    def uploadRequest(URI uri) {
        def url = "$uri/test-api/testupload2"
        def sizeInBytes = 222
        def data = new byte[sizeInBytes]
        new Random().nextBytes(data)
        def inputStreams = [
                uploadField('dataFile', 'foo.rnd', 'application/octet-stream'),
                new ByteArrayInputStream(data),
                new ByteArrayInputStream("\r\n--${boundaryString}--\r\n".bytes)
        ] as List<InputStream>

        loadClass('java.net.http.HttpRequest').newBuilder()
                .uri(URI.create(url))
                .header('content-type', "multipart/form-data;boundary=${boundaryString}")
                .header('accept', 'application/json')
                .POST(loadClass('java.net.http.HttpRequest$BodyPublishers').ofInputStream(new Supplier<InputStream>() {
                    @Override
                    InputStream get() {
                        new SequenceInputStream(Collections.enumeration(inputStreams))
                    }
                }))
                .build()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6532')
    @Timeout(10)
    def uploadTest() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'ConcurrentFormTransferSpec'
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        when:
        def client = loadClass('java.net.http.HttpClient').newBuilder()
                .version(loadClass('java.net.http.HttpClient$Version').HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build()

        def request = uploadRequest(embeddedServer.URI)
        def response = client.send(request, loadClass('java.net.http.HttpResponse$BodyHandlers').ofString())

        then:
        response.statusCode() == 200
        response.body() == "uploaded"

        cleanup:
        ctx.stop()
    }

    @Controller("/test-api")
    @io.micronaut.context.annotation.Requires(property = 'spec.name', value = 'ConcurrentFormTransferSpec')
    static class TransferController {
        @SuppressWarnings(['GrMethodMayBeStatic', 'unused'])
        @Post('/testupload2')
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Publisher<MutableHttpResponse<String>> uploadTest2(Publisher<StreamingFileUpload> dataFile) {
            def os = new OutputStream() {
                @Override
                void write(int b) throws IOException {
                }
            }
            return Flux.from(dataFile)
                    .flatMap { it.transferTo(os) }
                    .map { success -> success ? io.micronaut.http.HttpResponse.<String> ok('uploaded') : io.micronaut.http.HttpResponse.<String> status(HttpStatus.INTERNAL_SERVER_ERROR, 'error 1') }
        }
    }
}
