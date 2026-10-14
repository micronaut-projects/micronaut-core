package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * A disk-backed upload whose file is moved with {@link CompletedFileUpload#moveResource()} is
 * still closed by the server when the request ends. That close has nothing left to delete, but it
 * must release the leak tracker of the original upload, or the leak presence detector reports it.
 */
class MovedUploadLeakSpec extends Specification {
    def 'moving a disk upload does not leak the original'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                      : 'MovedUploadLeakSpec',
                'micronaut.server.multipart.disk': true,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def response = client.retrieve(HttpRequest.POST(
                '/moved-upload-leak',
                MultipartBody.builder().addPart('file', 'file.txt', MediaType.TEXT_PLAIN_TYPE, 'foo'.getBytes(StandardCharsets.UTF_8)).build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)

        then:
        response == 'false foo'

        cleanup:
        client.close()
        ctx.close()
    }

    @Controller('/moved-upload-leak')
    @Requires(property = 'spec.name', value = 'MovedUploadLeakSpec')
    static class Ctrl {
        @ExecuteOn(TaskExecutors.BLOCKING)
        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        String post(@Part CompletedFileUpload file) {
            CompletedFileUpload moved = file.moveResource()
            try {
                return moved.inMemory.toString() + ' ' + new String(moved.bytes, StandardCharsets.UTF_8)
            } finally {
                moved.close()
            }
        }
    }
}
