package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import jakarta.annotation.Nullable

import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder
import java.util.function.Supplier

// java.net.http.HttpClient
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

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/10851')
    @Timeout(15)
    def 'optional query value does not force streaming upload materialization'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'ConcurrentFormTransferSpec'
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()
        HttpClient httpClient = ctx.createBean(HttpClient, embeddedServer.URI)
        def client = httpClient.toBlocking()
        byte[] data = new byte[1024 * 1024]
        new Random(1).nextBytes(data)

        when:
        Map<String, Number> noQueryResponse = client.exchange(
                HttpRequest.POST('/test-api/streaming-query', multipartBody(data))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                Map
        ).body()

        Map<String, Number> withQueryResponse = client.exchange(
                HttpRequest.POST('/test-api/streaming-query?md5=present', multipartBody(data))
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                Map
        ).body()

        then:
        noQueryResponse.totalBytes.longValue() == data.length
        withQueryResponse.totalBytes.longValue() == data.length
        noQueryResponse.chunkCount.longValue() >= 1
        withQueryResponse.chunkCount.longValue() >= 1
        noQueryResponse.maxChunkSize.longValue() < data.length
        withQueryResponse.maxChunkSize.longValue() < data.length

        cleanup:
        httpClient.close()
        ctx.stop()
    }

    private static MultipartBody multipartBody(byte[] data) {
        MultipartBody.builder()
                .addPart('datasetFile', 'file.bin', MediaType.APPLICATION_OCTET_STREAM_TYPE, new ByteArrayInputStream(data), data.length)
                .build()
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
                    .then(Mono.just(HttpResponse.<String> ok('uploaded')))
        }

        @SuppressWarnings(['GrMethodMayBeStatic', 'unused'])
        @Post('/streaming-query{?md5}')
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        Mono<Map<String, Number>> streamingQuery(@io.micronaut.http.annotation.QueryValue @Nullable String md5, StreamingFileUpload datasetFile) {
            def chunkCount = new LongAdder()
            def totalBytes = new LongAdder()
            def maxChunkSize = new AtomicLong()
            def body = datasetFile.streamingBody()
            return Flux.from(body.toReadBufferPublisher())
                    .doOnNext { readBuffer ->
                        try {
                            int size = readBuffer.readable()
                            chunkCount.increment()
                            totalBytes.add(size)
                            maxChunkSize.accumulateAndGet(size as long, Math::max)
                        } finally {
                            readBuffer.close()
                        }
                    }
                    .doFinally { body.close() }
                    .then(Mono.fromSupplier {
                        [
                                chunkCount  : chunkCount.longValue(),
                                maxChunkSize: maxChunkSize.get(),
                                totalBytes  : totalBytes.longValue()
                        ]
                    })
        }
    }
}
