package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class FileUploadSpec extends Specification {
    def 'leak with inputstream getter'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'FileUploadSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.exchange(HttpRequest.POST(
                '/multipart/complete-file-upload',
                MultipartBody.builder().addPart('data', 'name', 'foo'.bytes).build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
        then:
        response.body() == 'Uploaded 3 bytes'

        cleanup:
        client.close()
        server.stop()
    }

    def 'wrong content type'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'FileUploadSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.retrieve(HttpRequest.POST(
                '/multipart/deserialize',
                MultipartBody.builder().addPart('metadata', 'metadata', MediaType.APPLICATION_OCTET_STREAM_TYPE, '{"foo":"bar"}'.bytes).build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
        then:
        response == 'Metadata: null'

        cleanup:
        client.close()
        server.stop()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10578")
    def 'publisher of completed'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'FileUploadSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        byte[] body1 = ("--------------------------76f6e44be9b3575a\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"image1.jpeg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\n" +
                "foo\r\n" +
                "--------------------------76f6e44be9b3575a\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"image2.jpeg\"\r\n" +
                "Content-Type: image/jpeg\r\n\r\n" +
                ("bar" * 10000)).getBytes(StandardCharsets.UTF_8)
        byte[] body2 = ("baz\r\n" +
                "--------------------------76f6e44be9b3575a--").getBytes(StandardCharsets.UTF_8)

        when:
        def connection = (HttpURLConnection) new URL("http://$server.host:$server.port/multipart/publisher-completed").openConnection()
        connection.setRequestMethod("POST")
        connection.addRequestProperty("Content-Type", "multipart/form-data; boundary=------------------------76f6e44be9b3575a")
        connection.setDoOutput(true)
        connection.setDoInput(true)
        connection.setChunkedStreamingMode(0)
        connection.connect()

        connection.outputStream.write(body1)
        connection.outputStream.flush()
        connection.outputStream.write(body2)
        connection.outputStream.close()

        def response = new String(connection.inputStream.readAllBytes(), StandardCharsets.UTF_8)
        then:
        response == 'Files: 2'

        cleanup:
        client.close()
        server.stop()
    }

    @Controller('/multipart')
    @Requires(property = 'spec.name', value = 'FileUploadSpec')
    @Produces(MediaType.TEXT_PLAIN)
    static class MultipartController {
        @Post(value = '/complete-file-upload', consumes = MediaType.MULTIPART_FORM_DATA)
        String completeFileUpload(CompletedFileUpload data) {
            def bytes = data.inputStream.bytes
            return "Uploaded ${bytes.length} bytes"
        }

        @Post(value = '/deserialize', consumes = MediaType.MULTIPART_FORM_DATA)
        String completeFileUpload(@Nullable @Part("metadata") Metadata metadata) {
            return "Metadata: " + metadata
        }

        @Post(value = '/publisher-completed', consumes = MediaType.MULTIPART_FORM_DATA)
        @SingleResult
        Publisher<String> publisherCompleted(@Nullable @Part("file") Publisher<CompletedFileUpload> files, @Nullable @Part("metadata") Metadata metadata) {
            return Flux.from(files)
                    .collectList()
                    .map { l ->
                        l.forEach { it.discard() }
                        "Files: " + l.size()
                    }
        }
    }

    record Metadata(@Nullable String foo) {}
}
