package io.micronaut.http.server.netty.consumes

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ContentTypeHeaderSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared RxHttpClient client = embeddedServer.applicationContext.createBean(
            RxHttpClient,
            embeddedServer.getURL()
    )

    void "test that content type header is ignored for methods that don't support a body"() {
        when:"A request is sent with a content type header"
        HttpResponse<String> resp = client.toBlocking().exchange(
                HttpRequest.GET("/test/content-type/get")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_XML), String
        )

        then:"The request succeeds, the content-type header is ignored"
        resp.status() == HttpStatus.OK
        resp.body() == 'ok'
    }

    void "test that content type header is ignored when it contains an empty string"() {
        when:"A request is sent with a content type header"
        HttpResponse<String> resp = client.toBlocking().exchange(
                HttpRequest.GET("/test/content-type/get")
                        .header(HttpHeaders.CONTENT_TYPE, ""), String
        )

        then:"The request succeeds, the content-type header is ignored"
        resp.status() == HttpStatus.OK
        resp.body() == 'ok'
    }


    @Controller("/test/content-type")
    static class ContentTypeController {

        @Get("/get")
        String get() {
            return "ok"
        }

    }

}
