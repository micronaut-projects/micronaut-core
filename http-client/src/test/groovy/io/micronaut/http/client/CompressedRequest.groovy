package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import java.util.zip.GZIPOutputStream

class CompressedRequest extends Specification {
    
    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)

    void "test gzipped body in post request"() {
        given:
        RxHttpClient client = RxHttpClient.create(embeddedServer.getURL())
        byte[] body = gzip("[0, 1, 2, 3, 4]")

        when:
        int i = 0
        HttpResponse<List> result = client.exchange(HttpRequest.POST('/gzip/request/numbers', body).contentEncoding(io.netty.handler.codec.http.HttpHeaderValues.GZIP).contentType(MediaType.APPLICATION_JSON_TYPE), List).blockingFirst()

        then:
        result.body().size() == 5
        result.body() == [0, 1, 2, 3, 4]

        cleanup:
        client.stop()
        client.close()
    }

    @Controller('/gzip/request')
    static class StreamController {

        @Post("/numbers")
        Single<List<Long>> numbers(@Header MediaType contentType, @Body Single<List<Long>> numbers) {
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
