package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.annotation.SingleResult
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.netty.handler.codec.http.HttpHeaderValues
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.zip.GZIPOutputStream

class CompressedRequest extends Specification {

    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'CompressedRequest'
    ])

    @AutoCleanup
    HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test gzipped body in post request"() {
        given:
        byte[] body = gzip("[0, 1, 2, 3, 4]")

        when:
        int i = 0
        HttpResponse<List> result = client.toBlocking().exchange(
                HttpRequest.POST('/gzip/request/numbers', body)
                        .contentEncoding(HttpHeaderValues.GZIP)
                        .contentType(MediaType.APPLICATION_JSON_TYPE), List)

        then:
        result.body().size() == 5
        result.body() == [0, 1, 2, 3, 4]
    }

    @Requires(property = 'spec.name', value = 'CompressedRequest')
    @Controller('/gzip/request')
    static class StreamController {

        @Post("/numbers")
        @SingleResult
        Publisher<List<Long>> numbers(@Header MediaType contentType, @Body Publisher<List<Long>> numbers) {
            assert contentType == MediaType.APPLICATION_JSON_TYPE
            numbers
        }
    }

    byte[] gzip(String s) {
        def targetStream = new ByteArrayOutputStream()
        def zipStream = new GZIPOutputStream(targetStream)
        zipStream.write(s.getBytes('UTF-8'))
        zipStream.close()
        byte[] zippedBytes = targetStream.toByteArray()
        targetStream.close()
        zippedBytes
    }
}
