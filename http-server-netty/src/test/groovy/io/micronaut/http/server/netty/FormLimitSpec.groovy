package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import reactor.core.publisher.Flux
import spock.lang.Specification

class FormLimitSpec extends Specification {
    def 'field count limit'(boolean multipart, int toSend, int limitToConfigure) {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                                      : 'FormLimitSpec',
                'micronaut.server.port'                          : -1,
                'micronaut.server.netty.form-max-fields'         : limitToConfigure,
                'micronaut.http.client.exception-on-error-status': false
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        def body

        if (multipart) {
            def builder = io.micronaut.http.client.multipart.MultipartBody.builder()
            for (int i = 0; i < toSend; i++) {
                builder.addPart("f" + i, "val")
            }
            body = builder.build()
        } else {
            StringBuilder builder = new StringBuilder()
            for (int i = 0; i < toSend; i++) {
                if (builder.length() > 0) builder.append('&')
                builder.append('f').append(i).append('=').append('val')
            }
            body = builder.toString()
        }

        when:
        HttpResponse<String> response = client.exchange(HttpRequest.POST("/form-limit", body)
                .contentType(multipart ? MediaType.MULTIPART_FORM_DATA_TYPE : MediaType.APPLICATION_FORM_URLENCODED_TYPE), Argument.STRING, Argument.STRING)
        then:
        if (toSend > limitToConfigure) {
            assert response.status() == HttpStatus.REQUEST_ENTITY_TOO_LARGE
        } else {
            assert response.body() == "attributes: " + toSend
        }

        where:
        multipart | toSend | limitToConfigure
        false     | 100    | 128
        false     | 100    | 64
        true      | 100    | 128
        true      | 100    | 64
    }

    def 'field name length limit'(boolean multipart, int toSend, int limitToConfigure) {
        given:
        def ctx = ApplicationContext.run([
                'spec.name'                                      : 'FormLimitSpec',
                'micronaut.server.port'                          : -1,
                'micronaut.server.netty.form-max-buffered-bytes' : limitToConfigure,
                'micronaut.http.client.exception-on-error-status': false
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        def body = " " * toSend

        when:
        HttpResponse<String> response = client.exchange(HttpRequest.POST("/form-limit", body)
                .contentType(multipart ? MediaType.MULTIPART_FORM_DATA + ";boundary=abcdef" : MediaType.APPLICATION_FORM_URLENCODED), Argument.STRING, Argument.STRING)
        then:
        if (toSend > limitToConfigure) {
            assert response.status() == HttpStatus.REQUEST_ENTITY_TOO_LARGE
        } else {
            assert response.body() == "attributes: 0"
        }

        where:
        multipart | toSend | limitToConfigure
        false     | 100    | 128
        false     | 100    | 64
        /* client does not support raw multipart requests atm
        true      | 100    | 128
        true      | 100    | 64
         */
    }

    @Controller("/form-limit")
    @Requires(property = "spec.name", value = "FormLimitSpec")
    static class MyCtrl {
        @Post
        @ExecuteOn(TaskExecutors.BLOCKING)
        @Consumes([MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA])
        String post(@Body MultipartBody body) {
            return "attributes: " + Flux.from(body).doOnNext { it.getBytes() }.count().block()
        }
    }
}
