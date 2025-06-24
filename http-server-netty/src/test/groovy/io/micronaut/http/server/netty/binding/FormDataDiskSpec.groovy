package io.micronaut.http.server.netty.binding

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

class FormDataDiskSpec extends Specification {
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6705')
    void "test parsing form to map with disk attributes"() {
        given:
        def server = (EmbeddedServer) ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.disk': true,
                'micronaut.server.multipart.mixed': true,
                'micronaut.server.thread-selection': 'IO',
                "spec.name": "FormDataDiskSpec"
        ])
        def client = server.applicationContext.createBean(HttpClient, server.URI)

        when:
        HttpResponse<?> response = Flux.from(client.exchange(HttpRequest.POST('/form-disk/object', [
                name:"Fred"
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String)).blockFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == '{"name":"Fred"}'

        cleanup:
        server.stop()
        client.stop()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/6705')
    void "test parsing form to map with mixed attributes"() {
        given:
        def server = (EmbeddedServer) ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.multipart.mixed': true,
                'micronaut.server.thread-selection': 'IO',
                "spec.name": "FormDataDiskSpec"
        ])
        def client = server.applicationContext.createBean(HttpClient, server.URI)

        when:
        HttpResponse<?> response = Flux.from(client.exchange(HttpRequest.POST('/form-disk/object', [
                name:"Fred",
        ]).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), String)).blockFirst()

        then:
        response.status == HttpStatus.OK
        response.body.isPresent()
        response.body.get() == '{"name":"Fred"}'

        cleanup:
        server.stop()
        client.stop()
    }

    @Controller(value = '/form-disk', consumes = MediaType.APPLICATION_FORM_URLENCODED)
    @Requires(property = "spec.name", value = "FormDataDiskSpec")
    static class FormController {
        @Post('/object')
        Object object(@Body Object object) {
            object
        }
    }
}
