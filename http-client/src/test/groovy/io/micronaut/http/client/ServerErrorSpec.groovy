package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.hateos.JsonError
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ServerErrorSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
    @Shared MyClient myClient = embeddedServer.getApplicationContext().getBean(MyClient)

    void "test 500 error"() {
        when:
        myClient.fiveHundred()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Bad things happening"
    }

    void "test 500 error - single"() {
        when:
        myClient.fiveHundredSingle().blockingGet()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Bad things happening"
    }

    void "test exception error"() {
        when:
        myClient.exception()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    void "test exception error - single"() {
        when:
        myClient.exceptionSingle().blockingGet()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    void "test single error"() {
        when:
        myClient.singleError()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    void "test single error - single"() {
        when:
        myClient.singleErrorSingle().blockingGet()

        then:
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad things happening"
    }

    @Client('/server-errors')
    static interface MyClient {
        @Get('/five-hundred')
        HttpResponse fiveHundred()

        @Get('/five-hundred')
        Single fiveHundredSingle()

        @Get('/exception')
        HttpResponse exception()

        @Get('/exception')
        Single exceptionSingle()

        @Get('/single-error')
        HttpResponse singleError()

        @Get('/single-error')
        Single singleErrorSingle()
    }

    @Controller('/server-errors')
    static class ServerErrorController {

        @Get
        HttpResponse fiveHundred() {
            HttpResponse.serverError()
                        .body(new JsonError("Bad things happening"))
        }

        @Get
        HttpResponse exception() {
            throw new RuntimeException("Bad things happening")
        }

        @Get
        Single singleError() {
            Single.error(new RuntimeException("Bad things happening"))
        }
    }
}
