package io.micronaut.upload

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.StreamingHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.multipart.StreamingFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.Specification

class UploadFilterErrorSpec extends Specification {

    void "test an upload that returns 401"() {
        Map<String, Object> config =  new LinkedHashMap<>()
        config.put("spec.name", "UploadFilterErrorSpec")
        config.put("micronaut.http.client.read-timeout", "10m")
        config.put("micronaut.netty.event-loops.other.num-threads", 10)
        config.put("micronaut.http.client.event-loop-group", "other")
        EmbeddedServer server = ApplicationContext.builder(config).run(EmbeddedServer)
        StreamingHttpClient client = server.getApplicationContext().createBean(StreamingHttpClient, server.getURL())
        MultipartBody.Builder builder = MultipartBody.builder()
        builder.addPart(
                "file",
                "file.txt",
                MediaType.TEXT_PLAIN_TYPE,
                new byte[200000]
        )
        MutableHttpRequest<MultipartBody> req = HttpRequest.POST("/upload/unauthorized", builder.build()) // set body
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)

        when:
        client.toBlocking().retrieve(req)

        then:
        def ex = thrown(HttpClientResponseException)
        ex.message.contains("Unauthorized")


        when:
        client.toBlocking().retrieve(req)

        then:
        ex = thrown(HttpClientResponseException)
        ex.message.contains("Unauthorized")
    }

    @Requires(property = "spec.name", value = "UploadFilterErrorSpec")
    @Controller
    static class UploadUnauthorizedController {

        @Post(uri = "/upload/unauthorized", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String upload(StreamingFileUpload file) {
            //will never be called
            return "ok"
        }
    }

    @Requires(property = "spec.name", value = "UploadFilterErrorSpec")
    @Filter("/upload/unauthorized")
    static class ErrorFilter implements HttpServerFilter {
        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.just(HttpResponse.unauthorized())
        }
    }
}
