package io.micronaut.http.server.netty.errors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ErrorMissingArgumentSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'ErrorMissingArgumentSpec'
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    @Unroll("for missing #uri message is #message")
    void "if bindable argument is missing a more detailed error message should be generated"(String uri,
                                                                                             String message) {
        when:
        client.exchange(HttpRequest.GET(uri), Map)

        then:
        HttpClientResponseException ex = thrown()
        ex.response.status == HttpStatus.BAD_REQUEST

        when:
        Map payload = ex.response.body()

        then:
        payload.message == message

        where:
        uri                            | message
        '/header'                      | 'Required Header [X-API-Version] not specified'
        '/cookie'                      | 'Required CookieValue [myCookie] not specified'
        '/headerNoValue'               | 'Required Header [apiVersion] not specified'
        '/cookieNoValue'               | 'Required CookieValue [myCookie] not specified'
        '/queryvalue'                  | 'Required QueryValue [number-of-items] not specified'
        '/queryvalueNoValue'           | 'Required QueryValue [numberOfItems] not specified'
        '/body'                        | 'Required Body [numberOfItems] not specified'
        '/queryvalueWithoutAnnotation' | 'Required argument [numberOfItems] not specified'
    }

    @Requires(property = 'spec.name', value= 'ErrorMissingArgumentSpec')
    @Controller
    static class MissingArgumentController {
        @Get("/header")
        @Status(HttpStatus.OK)
        void header(@Header("X-API-Version") String apiVersion) {
        }

        @Get("/headerNoValue")
        @Status(HttpStatus.OK)
        void headerNoValue(@Header String apiVersion) {
        }

        @Get("/cookie")
        @Status(HttpStatus.OK)
        void cookie(@CookieValue("myCookie") String myCookie) {
        }

        @Get("/cookieNoValue")
        @Status(HttpStatus.OK)
        void cookieNoValue(@CookieValue String myCookie) {
        }

        @Get("/queryvalue")
        @Status(HttpStatus.OK)
        void queryvalue(@QueryValue("number-of-items") Integer numberOfItems) {
        }

        @Get("/queryvalueNoValue")
        @Status(HttpStatus.OK)
        void queryvalueNoValue(@QueryValue Integer numberOfItems) {
        }

        @Get("/queryvalueWithoutAnnotation")
        @Status(HttpStatus.OK)
        void queryvalueWithoutAnnotation(Integer numberOfItems) {
        }

        @Get("/body")
        @Status(HttpStatus.OK)
        void body(@Body Integer numberOfItems) {
        }
    }
}
