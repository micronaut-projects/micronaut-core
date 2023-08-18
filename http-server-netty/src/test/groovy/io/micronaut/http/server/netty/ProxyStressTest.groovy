package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import spock.lang.Specification

class ProxyStressTest extends Specification {
    def 'proxy multipart stress test'() {
        given:
        def ctx = ApplicationContext.run(['spec.name': 'ProxyStressTest'])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        for (var i = 0; i < 1000; i++) {
            client.retrieve(HttpRequest.POST("/proxy", MultipartBody.builder()
                    .addPart("data", "data")
                    .addPart("testEventCode", "testEventCode"))
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
        }
        then:
        true

        cleanup:
        ctx.close()
    }

    @Controller
    @Requires(property = "spec.name", value = "ProxyStressTest")
    static class MyController {
        @Inject
        ProxyHttpClient proxyHttpClient
        @Inject
        EmbeddedServer embeddedServer

        @Post(value = "/proxy", consumes = MediaType.MULTIPART_FORM_DATA)
        Publisher<MutableHttpResponse<?>> proxy(
                @Part String data,
                @Part String testEventCode,
                HttpRequest<?> originRequest
        ) {
            return proxyHttpClient.proxy(originRequest.mutate()
                    .uri(new URI(embeddedServer.URI.toString() + "/backend"))
                    .body(MultipartBody.builder()
                            .addPart("data", data)
                            .addPart("accessToken", "foo")))
        }

        @Post(value = "/backend", consumes = MediaType.MULTIPART_FORM_DATA)
        String backend() {
            return "xyz"
        }
    }
}
