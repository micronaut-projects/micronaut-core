package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.HttpVersion
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
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

    @Shared
    @AutoCleanup
    EmbeddedServer http2Server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                             : 'ContentEncodingResponseSpec',
            'micronaut.server.http-version'         : 'HTTP_2_0',
            'micronaut.server.ssl.enabled'          : true,
            'micronaut.server.ssl.build-self-signed': true,
            'micronaut.server.ssl.port'             : 0,
    ])

    @Shared
    @AutoCleanup
    HttpClient http2Client = http2Server.applicationContext.createBean(HttpClient, http2Server.getURL(), http2Configuration())

    private static DefaultHttpClientConfiguration http2Configuration() {
        def configuration = new DefaultHttpClientConfiguration()
        configuration.alpnModes = [HttpVersionSelection.ALPN_HTTP_2]
        configuration.sslConfiguration.insecureTrustAllCertificates = true
        configuration
    }

    private HttpClient client(HttpVersion version) {
        version == HttpVersion.HTTP_2_0 ? http2Client : client
    }

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

    void "content encoding header is kept on a not modified response"() {
        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/content-encoding/not-modified'), String)

        then:
        response.status() == HttpStatus.NOT_MODIFIED
        response.header(HttpHeaders.CONTENT_ENCODING) == 'gzip'
    }

    void "gzip stream of empty content is decoded and the header removed"() {
        when:
        HttpResponse<byte[]> response = client(version).toBlocking().exchange(HttpRequest.GET('/content-encoding/gzip-empty'), byte[])

        then:
        response.header('X-Http-Version') == version.name()
        !response.headers.contains(HttpHeaders.CONTENT_ENCODING)
        response.getBody(byte[]).orElse(new byte[0]).length == 0

        where:
        version << [HttpVersion.HTTP_1_1, HttpVersion.HTTP_2_0]
    }

    void "HTTP/2 content encoding is handled like HTTP/1.1"() {
        when:
        HttpResponse<String> response = http2Client.toBlocking().exchange(HttpRequest.GET(path), String)

        then:
        response.header('X-Http-Version') == HttpVersion.HTTP_2_0.name()
        response.header(HttpHeaders.CONTENT_ENCODING) == expectedEncoding
        response.getBody(String).orElse(null) == expectedBody

        where:
        path                               | expectedEncoding | expectedBody
        '/content-encoding/unknown'        | 'someencoding'   | BODY
        '/content-encoding/gzip'           | null             | BODY
        '/content-encoding/deflate'        | null             | BODY
        '/content-encoding/empty/gzip'     | 'gzip'           | null
        '/content-encoding/empty/deflate'  | 'deflate'        | null
        '/content-encoding/no-content'     | 'gzip'           | null
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

        @Get('/gzip-empty')
        HttpResponse<byte[]> gzipEmpty() {
            HttpResponse.ok(gzip(new byte[0]))
                    .header(HttpHeaders.CONTENT_ENCODING, 'gzip')
        }

        @Get('/not-modified')
        HttpResponse<?> notModified() {
            HttpResponse.notModified().header(HttpHeaders.CONTENT_ENCODING, 'gzip')
        }

        @Get('/no-content')
        HttpResponse<?> noContent() {
            HttpResponse.noContent().header(HttpHeaders.CONTENT_ENCODING, 'gzip')
        }
    }

    @Requires(property = 'spec.name', value = 'ContentEncodingResponseSpec')
    @ServerFilter('/content-encoding/**')
    static class HttpVersionFilter {

        @ResponseFilter
        void httpVersion(HttpRequest<?> request, MutableHttpResponse<?> response) {
            response.header('X-Http-Version', request.httpVersion.name())
        }
    }
}
