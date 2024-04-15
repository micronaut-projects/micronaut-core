package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
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
import spock.lang.Specification

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
    }

    record Metadata(@Nullable String foo) {}
}
