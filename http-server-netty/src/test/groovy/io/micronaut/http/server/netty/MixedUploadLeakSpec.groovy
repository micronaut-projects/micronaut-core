package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.runtime.server.EmbeddedServer
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import spock.lang.Issue
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import java.util.stream.IntStream

class MixedUploadLeakSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/7699')
    def 'leak test'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'MixedUploadLeakSpec',
                'micronaut.server.max-request-size': '5000GB',
                'micronaut.server.multipart.enabled': true,
                'micronaut.server.multipart.max-file-size': '5000GB',
                'micronaut.server.multipart.mixed': true,
        ])
        def embeddedServer = ctx.getBean(EmbeddedServer)
        embeddedServer.start()

        HttpPost request = new HttpPost(embeddedServer.URI.toString() + "/fdhcpLeak");

        def httpClient = HttpClients.custom()
                .setMaxConnPerRoute(50)
                .setMaxConnTotal(200)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(60000)
                        .build())
                .evictIdleConnections(5, TimeUnit.MINUTES)
                .evictExpiredConnections()
                .build()

        def bigFile = IntStream.range(0, 20000000).mapToObj(i -> "Data").collect(Collectors.joining()).getBytes(StandardCharsets.UTF_8)
        final MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create()
                .addTextBody("foo", "bar")
                .addBinaryBody("file", bigFile, ContentType.TEXT_PLAIN, "any-path.txt")

        request.setEntity(multipartEntityBuilder.build())

        when:
        def response = httpClient.execute(request)
        def body = EntityUtils.toString(response.entity)
        then:
        body == 'bar ' + (20000000 * 4)

        cleanup:
        embeddedServer.stop()
        httpClient.close()
    }

    @Controller('/fdhcpLeak')
    @Requires(property = 'spec.name', value = 'MixedUploadLeakSpec')
    static class Ctrl {
        @Post(consumes = MediaType.MULTIPART_FORM_DATA)
        def post(
                @Part String foo,
                @Part CompletedFileUpload file
        ) {
            try {
                return foo + " " + file.size
            } finally {
                file.discard()
            }
        }
    }
}
