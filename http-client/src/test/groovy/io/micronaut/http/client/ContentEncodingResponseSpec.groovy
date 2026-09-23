package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream

class ContentEncodingResponseSpec extends Specification {

    static final String BODY = "Hello, encoding!"

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ContentEncodingResponseSpec'
    ])

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "unknown content encoding is passed through with the header"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/content-encoding/unknown'), String)

        then:
        response.header(HttpHeaders.CONTENT_ENCODING) == 'someencoding'
        response.body() == BODY
    }

    void "gzip content encoding is decoded and the header removed"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/content-encoding/gzip'), String)

        then:
        !response.headers.contains(HttpHeaders.CONTENT_ENCODING)
        response.body() == BODY
    }

    void "deflate content encoding is decoded and the header removed"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/content-encoding/deflate'), String)

        then:
        !response.headers.contains(HttpHeaders.CONTENT_ENCODING)
        response.body() == BODY
    }

    void "content encoding header is kept on a response without a body"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/content-encoding/empty/$encoding"), String)

        then:
        response.status() == HttpStatus.OK
        response.header(HttpHeaders.CONTENT_ENCODING) == encoding
        !response.body.isPresent()

        where:
        encoding << ['gzip', 'deflate', 'someencoding']
    }

    void "content encoding header is kept on a no content response"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/content-encoding/no-content'), String)

        then:
        response.status() == HttpStatus.NO_CONTENT
        response.header(HttpHeaders.CONTENT_ENCODING) == 'gzip'
    }

    static byte[] gzip(byte[] data) {
        def out = new ByteArrayOutputStream()
        new GZIPOutputStream(out).withCloseable { it.write(data) }
        out.toByteArray()
    }

    static byte[] deflate(byte[] data) {
        def out = new ByteArrayOutputStream()
        new DeflaterOutputStream(out).withCloseable { it.write(data) }
        out.toByteArray()
    }

    @Requires(property = 'spec.name', value = 'ContentEncodingResponseSpec')
    @Controller('/content-encoding')
    static class ContentEncodingController {

        @Get('/unknown')
        HttpResponse<byte[]> unknown() {
            HttpResponse.ok(BODY.getBytes(StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_ENCODING, 'someencoding')
        }

        @Get('/gzip')
        HttpResponse<byte[]> gzip() {
            HttpResponse.ok(gzip(BODY.getBytes(StandardCharsets.UTF_8)))
                    .header(HttpHeaders.CONTENT_ENCODING, 'gzip')
        }

        @Get('/deflate')
        HttpResponse<byte[]> deflate() {
            HttpResponse.ok(deflate(BODY.getBytes(StandardCharsets.UTF_8)))
                    .header(HttpHeaders.CONTENT_ENCODING, 'deflate')
        }

        @Get('/empty/{encoding}')
        HttpResponse<?> empty(String encoding) {
            HttpResponse.ok().header(HttpHeaders.CONTENT_ENCODING, encoding)
        }

        @Get('/no-content')
        HttpResponse<?> noContent() {
            HttpResponse.noContent().header(HttpHeaders.CONTENT_ENCODING, 'gzip')
        }
    }
}
