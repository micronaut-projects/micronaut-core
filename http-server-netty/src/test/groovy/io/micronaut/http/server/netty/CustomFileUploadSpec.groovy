package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Headers
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.body.MessageBodyReader
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.codec.CodecException
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import org.junit.Assert
import org.jvnet.mimepull.Header
import org.jvnet.mimepull.MIMEMessage
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.stream.Collectors

class CustomFileUploadSpec extends Specification {

    def 'custom file upload'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'CustomFileUploadSpec'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.exchange(HttpRequest.POST(
                '/multipart/file-upload',
                MultipartBody.builder().addPart('MyName', 'myFile', 'foo'.bytes).build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
        then:
        response.body() == 'Uploaded content-disposition: form-data; name="MyName"; filename="myFile", content-length: 3, content-type: application/octet-stream, content-transfer-encoding: binary foo'

        cleanup:
        client.close()
        server.stop()
    }

    @Controller('/multipart')
    @Requires(property = 'spec.name', value = 'CustomFileUploadSpec')
    @Produces(MediaType.TEXT_PLAIN)
    static class MultipartController {
        @Post(value = '/file-upload', consumes = MediaType.MULTIPART_FORM_DATA)
        String completeFileUpload(@Body MyFileUpload data) {
            return "Uploaded " + data.value()
        }

    }

    record MyFileUpload(String value) {
    }

    @Singleton
    @Consumes("multipart/form-data")
    static class MyFileUpdateReader implements MessageBodyReader<MyFileUpload> {

        @Override
        MyFileUpload read(Argument<MyFileUpload> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            MIMEMessage mimeMessage = new MIMEMessage(inputStream, mediaType.getParameters().get("boundary").orElse(""))
            mimeMessage.parseAll()
            def attachments = mimeMessage.getAttachments()
            Assert.assertEquals(1, attachments.size())
            def part = attachments.get(0)
            def headers = part.getAllHeaders().stream().map { Header h -> h.name + ": " + h.value }.collect(Collectors.joining(", "))
            return new MyFileUpload(headers + " " + new String(part.read().readAllBytes(), StandardCharsets.UTF_8))
        }
    }

    record Metadata(@Nullable String foo) {}
}
