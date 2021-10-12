package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import io.micronaut.core.async.annotation.SingleResult
import spock.lang.Ignore
import spock.lang.Specification

class MaxRequestSizeSpec extends Specification {

    void "test max request size default processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.maxRequestSize': '10KB'])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        byte[] kb10 = new byte[10240]
        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/text", new String(kb10)).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        result == "OK"

        when:
        byte[] kb101 = new byte[10241]
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/text", new String(kb101)).contentType(MediaType.TEXT_PLAIN_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max request size json processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.maxRequestSize': '10KB'])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        String json = '{"x":"' + ('y' * (10240 - 8)) + '"}'
        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/json", json).contentType(MediaType.APPLICATION_JSON_TYPE))

        then:
        result == "OK"

        when:
        json = '{"x":"' + ('y' * (10240 - 7)) + '"}'
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/json", new String(json)).contentType(MediaType.APPLICATION_JSON_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    @Ignore("Whether or not the exception is thrown is inconsistent. I don't think there is anything we can do to ensure its consistency")
    void "test max request size multipart processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.maxRequestSize': '10KB'])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1872])
                .addPart("b", "b.pdf", new byte[1872])
                .addPart("c", "c.pdf", new byte[1872])
                .addPart("d", "d.pdf", new byte[1871])
                .addPart("e", "e.pdf", new byte[1871])
                .build()

        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

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
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The content length [10241] exceeds the maximum allowed content length [10240]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max part size multipart processor"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.maxFileSize': '1KB'
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1024])
                .build()

        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

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
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The part named [e] exceeds the maximum allowed content length [1024]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test max part size multipart body binder"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.maxFileSize': '1KB'
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[1024])
                .addPart("b", "b.pdf", new byte[1024])
                .addPart("c", "c.pdf", new byte[1024])
                .addPart("d", "d.pdf", new byte[1024])
                .addPart("e", "e.pdf", new byte[1024])
                .build()

        String result = client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart-body", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

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
        client.toBlocking().retrieve(HttpRequest.POST("/test-max-size/multipart-body", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message == "The part named [e] exceeds the maximum allowed content length [1024]"

        cleanup:
        client.close()
        embeddedServer.close()
    }

    void "test content length exceeded with different disk storage"() {
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.maxRequestSize': '10KB',
                'micronaut.server.multipart.disk': true
        ])
        HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

        when:
        MultipartBody body = MultipartBody.builder()
                .addPart("a", "a.pdf", new byte[20240])
                .build()

        String result = Flux.from(client.retrieve(HttpRequest.POST("/test-max-size/multipart-body", body)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE))).blockFirst()

        then:
        def ex = thrown(HttpClientResponseException)
        ex.response.getBody(Map).get()._embedded.errors[0].message.contains("exceeds the maximum allowed content length [10240]")

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
        @SingleResult
        Publisher<String> multipart(@Body io.micronaut.http.server.multipart.MultipartBody body) {
            return Flux.from(body).collectList().map({ list -> "OK" })
        }
    }
}
