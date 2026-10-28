package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.http.HttpHeaderNames
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.concurrent.ThreadLocalRandom
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream

class TruncatedCompressedRequestSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'TruncatedCompressedRequestSpec'])

    @Shared
    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "complete compressed request is read: #contentEncoding"() {
        expect:
        post(contentEncoding, 0) == "1000"

        where:
        contentEncoding << ['gzip', 'deflate']
    }

    void "compressed request that ends before its compressed stream is not read as complete: #contentEncoding missing #missing"() {
        when:
        post(contentEncoding, missing)

        then:
        thrown(HttpClientResponseException)

        where:
        contentEncoding | missing
        'gzip'          | 500
        'gzip'          | 8
        'deflate'       | 500
    }

    private String post(String contentEncoding, int missing) {
        byte[] content = new byte[1000]
        ThreadLocalRandom.current().nextBytes(content)
        byte[] compressed = compress(contentEncoding, content)
        byte[] sent = Arrays.copyOf(compressed, compressed.length - missing)
        return client.toBlocking().retrieve(HttpRequest.POST('/truncated-compressed', sent)
                .header(HttpHeaderNames.CONTENT_ENCODING, contentEncoding)
                .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE), String)
    }

    private static byte[] compress(String contentEncoding, byte[] content) {
        def output = new ByteArrayOutputStream()
        def stream = contentEncoding == 'gzip' ? new GZIPOutputStream(output) : new DeflaterOutputStream(output)
        stream.withCloseable {
            it.write(content)
        }
        output.toByteArray()
    }

    @Requires(property = 'spec.name', value = 'TruncatedCompressedRequestSpec')
    @Controller("/truncated-compressed")
    static class TestController {
        @Post(consumes = MediaType.APPLICATION_OCTET_STREAM)
        String post(@Body byte[] body) {
            return String.valueOf(body.length)
        }
    }
}
