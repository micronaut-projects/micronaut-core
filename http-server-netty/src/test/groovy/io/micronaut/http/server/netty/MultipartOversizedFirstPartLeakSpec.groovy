package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Issue
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path

class MultipartOversizedFirstPartLeakSpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/12630')
    def 'rejecting an oversized first part releases its buffers'() {
        given:
        Path tmp = Files.createTempFile('oversized-', '.bin')
        // max-file-size defaults to 1MB, so 5MB is rejected
        Files.write(tmp, new byte[5 * 1024 * 1024])

        def ctx = ApplicationContext.run([
                'spec.name'                        : 'MultipartOversizedFirstPartLeakSpec',
                'micronaut.server.max-request-size': '10GB',
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer).start()
        def client = HttpClient.create(embeddedServer.URL)

        def body = MultipartBody.builder()
                // the oversized file part comes first, ahead of the attribute the controller also binds
                .addPart('file', tmp.fileName.toString(), MediaType.APPLICATION_OCTET_STREAM_TYPE, tmp.toFile())
                .addPart('hello', 'world')
                .build()

        when:
        client.toBlocking().exchange(
                HttpRequest.POST('/oversized-first-part/upload', body)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                String)

        then: 'the request is rejected, and the leak detector sees no unreleased buffer'
        thrown(HttpClientResponseException)

        cleanup:
        client.close()
        embeddedServer.stop()
        ctx.close()
        Files.deleteIfExists(tmp)
    }

    @Controller('/oversized-first-part')
    @Requires(property = 'spec.name', value = 'MultipartOversizedFirstPartLeakSpec')
    static class Ctrl {
        @Post(uri = '/upload', consumes = MediaType.MULTIPART_FORM_DATA)
        Mono<String> upload(String hello, StreamingFileUpload file) {
            File target = File.createTempFile('oversized-', '.bin')
            target.deleteOnExit()
            Mono.from(file.transferTo(target)).map(ok -> hello + ':' + target.name)
        }
    }
}
