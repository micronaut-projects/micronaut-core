package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import io.reactivex.Single
import spock.lang.Ignore
import spock.lang.Specification

class MaxRequestSizeSpec extends Specification {

    void "test max request size default processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.maxRequestSize': '10KB'])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        byte[] kb10 = new byte[10240]
        String result = client.retrieve(HttpRequest.POST("/test-max-size/text", new String(kb10)).contentType(MediaType.TEXT_PLAIN_TYPE)).blockingFirst()

        then:
        result == "OK"

        when:
        byte[] kb101 = new byte[10241]
        client.retrieve(HttpRequest.POST("/test-max-size/text", new String(kb101)).contentType(MediaType.TEXT_PLAIN_TYPE)).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max request size json processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.maxRequestSize': '10KB'])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        String json = '{"x":"' + ('y' * (10240 - 8)) + '"}'
        String result = client.retrieve(HttpRequest.POST("/test-max-size/json", json).contentType(MediaType.APPLICATION_JSON_TYPE)).blockingFirst()

        then:
        result == "OK"

        when:
        json = '{"x":"' + ('y' * (10240 - 7)) + '"}'
        client.retrieve(HttpRequest.POST("/test-max-size/json", new String(json)).contentType(MediaType.APPLICATION_JSON_TYPE)).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    @Ignore("Whether or not the exception is thrown is inconsistent. I don't think there is anything we can do to ensure its consistency")
    void "test max request size multipart processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.maxRequestSize': '10KB'])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1872])
                .addPart("b", "b.pdf", new byte[1872])
                .addPart("c", "c.pdf", new byte[1872])
                .addPart("d", "d.pdf", new byte[1871])
                .addPart("e", "e.pdf", new byte[1871])
                .build()

        String result = client.retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)).blockingFirst()

        then:
        result == "OK"

        when:
        body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1872])
                .addPart("b", "b.pdf", new byte[1872])
                .addPart("c", "c.pdf", new byte[1872])
                .addPart("d", "d.pdf", new byte[1871])
                .addPart("e", "e.pdf", new byte[1872]) //One extra byte
                .build()
        client.retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max part size multipart processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.maxFileSize': '1KB'
        ])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1024])
                .build()

        String result = client.retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)).blockingFirst()

        then:
        result == "OK"

        when:
        body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1025]) //One extra byte
                .build()
        client.retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "The part named [e] exceeds the maximum allowed content length [1024]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max part size multipart body binder"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.maxFileSize': '1KB'
        ])
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1024])
                .build()

        String result = client.retrieve(HttpRequest.POST("/test-max-size/multipart-body", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)).blockingFirst()

        then:
        result == "OK"

        when:
        body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1025]) //One extra byte
                .build()
        client.retrieve(HttpRequest.POST("/test-max-size/multipart-body", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE)).blockingFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message == "The part named [e] exceeds the maximum allowed content length [1024]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    @Controller("/test-max-size")
    static class TestController {

        @Post(uri = "/text", consumes = MediaType.TEXT_PLAIN)
        String multipart(@Body String body) {
            "OK"
        }

        @Post(uri = "/json", consumes = MediaType.APPLICATION_JSON)
        String json(@Body String body) {
            "OK"
        }

        @Post(uri = "/multipart", consumes = MediaType.MULTIPART_FORM_DATA)
        String multipart(CompletedFileUpload a,
                         CompletedFileUpload b,
                         CompletedFileUpload c,
                         CompletedFileUpload d,
                         CompletedFileUpload e) {
            "OK"
        }

        @Post(uri = "/multipart-body", consumes = MediaType.MULTIPART_FORM_DATA)
        Single<String> multipart(@Body io.micronaut.http.server.multipart.MultipartBody body) {
            return Flowable.fromPublisher(body).toList().map({ list -> "OK" })
        }
    }
}
