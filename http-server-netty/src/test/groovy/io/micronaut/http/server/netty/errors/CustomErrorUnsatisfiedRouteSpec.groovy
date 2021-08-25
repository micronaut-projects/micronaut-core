package io.micronaut.http.server.netty.errors

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.*
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.web.router.exceptions.UnsatisfiedHeaderRouteException
import io.micronaut.web.router.exceptions.UnsatisfiedRouteException
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class CustomErrorUnsatisfiedRouteSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'CustomErrorUnsatisfiedRouteSpec'
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    BlockingHttpClient getClient() {
        httpClient.toBlocking()
    }

    @Unroll("for missing #uri message is #message")
    void "should properly map errors"(HttpStatus status, String uri, String message) {
        when:
        client.exchange(HttpRequest.GET(uri), Map)

        then:
        HttpClientResponseException ex = thrown()
        ex.response.status == status

        when:
        Map payload = ex.response.body()

        then:
        payload.myMessage == message

        where:
        status                 | uri                  | message
        HttpStatus.BAD_REQUEST | '/header'            | 'Oh no: Required Header [X-API-Version] not specified'
        HttpStatus.BAD_REQUEST | '/cookie'            | 'Custom: Required CookieValue [myCookie] not specified'
        HttpStatus.BAD_REQUEST | '/headerNoValue'     | 'Oh no: Required Header [apiVersion] not specified'
        HttpStatus.BAD_REQUEST | '/cookieNoValue'     | 'Custom: Required CookieValue [myCookie] not specified'
        HttpStatus.BAD_REQUEST | '/queryvalue'        | 'Custom: Required QueryValue [number-of-items] not specified'
        HttpStatus.BAD_REQUEST | '/queryvalueNoValue' | 'Custom: Required QueryValue [numberOfItems] not specified'
        HttpStatus.BAD_REQUEST | '/body'              | 'Custom: Required Body [numberOfItems] not specified'
        HttpStatus.BAD_REQUEST | '/somethingbad1'     | 'Something bad'
        HttpStatus.BAD_REQUEST | '/somethingbad2'     | 'Something bad'
        HttpStatus.BAD_REQUEST | '/somethingbad3'     | 'Something bad'
        HttpStatus.I_AM_A_TEAPOT | '/somethingbad4'     | 'teapot'
    }

    void "not found is not mapped"(String uri) {
        when:
        client.exchange(HttpRequest.GET(uri), Map)

        then:
        HttpClientResponseException ex = thrown()
        ex.response.status == HttpStatus.NOT_FOUND

        when:
        Map payload = ex.response.body()

        then:
        payload._embedded.errors[0].message == message

        where:
        uri           | message
        '/not-found1' | "Page Not Found"
        '/not-found2' | "Page Not Found"
        '/not-found3' | "Page Not Found"
        '/not-found4' | "Page Not Found"
        '/not-found5' | "Page Not Found"
    }

    @Requires(property = 'spec.name', value = 'CustomErrorUnsatisfiedRouteSpec')
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

        @Get("/somethingbad1")
        @Status(HttpStatus.BAD_REQUEST)
        void somethingbad1() {
        }

        @Get("/somethingbad2")
        HttpResponse somethingbad2() {
            HttpResponse.badRequest()
        }

        @Get("/somethingbad3")
        void somethingbad3() {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, ['myMessage': "Message lost"])
        }

        @Get("/somethingbad4")
        void somethingbad4() {
            throw new HttpStatusException(HttpStatus.GONE, ['myMessage': "Message lost"])
        }

        @Get("/notfound1")
        @Status(HttpStatus.NOT_FOUND)
        void notfound1() {
        }

        @Get("/notfound2")
        Object notfound2() {
            return null
        }

        @Get("/notfound3")
        HttpResponse notfound3() {
            HttpResponse.notFound()
        }

        @Get("/notfound4")
        void notfound4() {
            throw new HttpStatusException(HttpStatus.NOT_FOUND, ['myMessage': "Message lost"])
        }

        @Get("/notfound5")
        HttpStatus notfound5() {
            HttpStatus.NOT_FOUND
        }

        @Error
        HttpResponse unsatisfiedRouteExceptionHandler(UnsatisfiedRouteException e) {
            HttpResponse.badRequest().body(['myMessage': "Custom: " + e.getMessage()])
        }

        @Error
        HttpResponse unsatisfiedHeaderRouteExceptionHandler(UnsatisfiedHeaderRouteException e) {
            HttpResponse.badRequest().body(['myMessage': "Oh no: " + e.getMessage()])
        }

        @Error(status = HttpStatus.BAD_REQUEST)
        Map<String, String> badRequest() {
            ['myMessage': "Something bad"]
        }

        @Error(status = HttpStatus.NOT_FOUND)
        HttpResponse notFound() {
            HttpResponse.badRequest().body(['myMessage': "Cannot find"])
        }

        @Error(status = HttpStatus.GONE)
        @Status(HttpStatus.I_AM_A_TEAPOT)
        Map<String, String> gone() {
            ['myMessage': "teapot"]
        }
    }
}
