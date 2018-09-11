package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CustomErrorTypeSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "test custom error type"() {

        given:
        CustomErrorClient client = embeddedServer.getApplicationContext().getBean(CustomErrorClient)

        when:
        client.index()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.getBody(MyError).get().reason == 'bad things'

    }

    @Controller('/test/custom-errors')
    static class CustomErrorController {

        @Get("/")
        HttpResponse index() {
            HttpResponse.serverError().body(new MyError(reason: "bad things"))
        }
    }

    @Client(value = '/test/custom-errors', errorType = MyError)
    static interface CustomErrorClient {
        @Get("/")
        HttpResponse index()
    }

    static class MyError {
        String reason
    }
}
