package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
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
import spock.lang.Specification
import spock.lang.Timeout

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.function.Supplier

@Requires(sdk = Requires.Sdk.JAVA, version = "11") // java.net.http.HttpClient
class ConcurrentFormTransferSpec extends Specification {
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

    HttpRequest uploadRequest(URI uri) {
        def url = "$uri/test-api/testupload2"
        def sizeInBytes = 222
        def data = new byte[sizeInBytes]
        new Random().nextBytes(data)
        def inputStreams = [
                uploadField('dataFile', 'foo.rnd', 'application/octet-stream'),
                new ByteArrayInputStream(data),
                new ByteArrayInputStream("\r\n--${boundaryString}--\r\n".bytes)
        ] as List<InputStream>

        HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header('content-type', "multipart/form-data;boundary=${boundaryString}")
                .header('accept', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofInputStream(new Supplier<InputStream>() {
                    @Override
                    InputStream get() {
                        new SequenceInputStream(Collections.enumeration(inputStreams))
                    }
                }))
                .build()
    }

    @Timeout(10)
    def uploadTest() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'ConcurrentFormTransferSpec'
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        when:
        def client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build()

        def request = uploadRequest(embeddedServer.URI)
        def response = client.send(request, HttpResponse.BodyHandlers.ofString()) as HttpResponse<String>

        println 'status code: ' + response.statusCode()
        println response.body()

        then:
        1 == 1

        cleanup:
        ctx.stop()
    }

    @Controller("/test-api")
    @Requires(property = 'spec.name', value = 'ConcurrentFormTransferSpec')
    static class TransferController {
        @SuppressWarnings(['GrMethodMayBeStatic', 'unused'])
        @Post('/testupload2')
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Publisher<MutableHttpResponse<String>> uploadTest2(Flux<StreamingFileUpload> dataFile) {
            def os = new OutputStream() {
                @Override
                void write(int b) throws IOException {
                }
            }
            return dataFile
                    .flatMap { it.transferTo(os) }
                    .map { success -> success ? io.micronaut.http.HttpResponse.<String> ok('uploaded') : io.micronaut.http.HttpResponse.<String> status(HttpStatus.INTERNAL_SERVER_ERROR, 'error 1') }
        }
    }
}
