package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.multipart.RawFormField
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class MultipartSingleFieldBindingSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext ctx = ApplicationContext.run([
            'spec.name'                                  : 'MultipartSingleFieldBindingSpec',
            'micronaut.server.multipart.max-file-size'   : '1KB',
            'micronaut.server.max-request-size'          : '10MB',
    ])
    @Shared EmbeddedServer server = ctx.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = ctx.createBean(HttpClient, server.URL)

    private String post(String path, MultipartBody body) {
        client.toBlocking().retrieve(HttpRequest.POST(path, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
    }

    def 'binds a single completed file upload, using the first of duplicate parts'() {
        expect:
        post('/single-field/file', MultipartBody.builder()
                .addPart('file', 'a.txt', MediaType.TEXT_PLAIN_TYPE, 'first'.bytes)
                .addPart('file', 'b.txt', MediaType.TEXT_PLAIN_TYPE, 'second'.bytes)
                .build()) == 'a.txt:first'
    }

    def 'binds a single @Part value'() {
        expect:
        post('/single-field/part', MultipartBody.builder().addPart('name', 'foo').addPart('other', 'x').build()) == 'foo'
    }

    def 'binds a raw form field'() {
        expect:
        post('/single-field/raw', MultipartBody.builder().addPart('field', 'bar').build()) == 'bar'
    }

    def 'missing required part is unsatisfied'() {
        when:
        post(path, MultipartBody.builder().addPart('unrelated', 'x').build())

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.BAD_REQUEST

        where:
        path << ['/single-field/file', '/single-field/part', '/single-field/raw']
    }

    def 'missing nullable part binds null'() {
        expect:
        post('/single-field/optional', MultipartBody.builder().addPart('unrelated', 'x').build()) == 'none'
    }

    def 'oversized file part propagates the size limit error'() {
        when:
        post(path, MultipartBody.builder()
                .addPart(name, 'big.bin', MediaType.APPLICATION_OCTET_STREAM_TYPE, new byte[4096])
                .build())

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.REQUEST_ENTITY_TOO_LARGE

        where:
        path                  | name
        '/single-field/file'  | 'file'
        '/single-field/bytes' | 'data'
    }

    def 'server keeps serving after errors'() {
        expect:
        post('/single-field/part', MultipartBody.builder().addPart('name', 'again').build()) == 'again'
    }

    @Controller('/single-field')
    @Requires(property = 'spec.name', value = 'MultipartSingleFieldBindingSpec')
    static class Ctrl {
        @Post(uri = '/file', consumes = MediaType.MULTIPART_FORM_DATA)
        String file(CompletedFileUpload file) {
            file.filename + ':' + new String(file.bytes, StandardCharsets.UTF_8)
        }

        @Post(uri = '/part', consumes = MediaType.MULTIPART_FORM_DATA)
        String part(@Part('name') String name) {
            name
        }

        @Post(uri = '/bytes', consumes = MediaType.MULTIPART_FORM_DATA)
        String bytes(@Part('data') byte[] data) {
            String.valueOf(data.length)
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Post(uri = '/raw', consumes = MediaType.MULTIPART_FORM_DATA)
        String raw(RawFormField field) {
            field.byteBody().toInputStream().withCloseable { it.text }
        }

        @Post(uri = '/optional', consumes = MediaType.MULTIPART_FORM_DATA)
        String optional(@Nullable @Part('name') String name) {
            name ?: 'none'
        }
    }
}
