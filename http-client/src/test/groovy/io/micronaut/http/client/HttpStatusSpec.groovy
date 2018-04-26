package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpStatusSpec extends Specification {
    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "Simple default return HttpStatus OK"() {
        given:
            HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
            Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.GET("/status/simple"), String
            ))
            HttpResponse<String> response = flowable.blockingFirst()
            Optional<String> body = response.getBody()

        then:
            response.status == HttpStatus.OK
            body.isPresent()
            body.get() == 'success'

        cleanup:
            client.stop()
            client.close()
    }

    void "Simple custom return HttpStatus CREATED"() {
        given:
            HttpClient client = new DefaultHttpClient(embeddedServer.getURL())

        when:
            Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.GET("/status/simpleCreated"), String
            ))
            HttpResponse<String> response = flowable.blockingFirst()
            Optional<String> body = response.getBody()

        then:
            response.status == HttpStatus.CREATED
            body.isPresent()
            body.get() == 'success'

        cleanup:
            client.stop()
            client.close()
    }

    void "Simple custom return HttpStatus 404"() {
        given:
            HttpClient client = HttpClient.create(embeddedServer.getURL())

        when:
            Flowable<HttpResponse<String>> flowable = Flowable.fromPublisher(client.exchange(
                    HttpRequest.GET("/status/simple404"), String
            ))
            HttpResponse<String> response = flowable.blockingFirst()

        then:
            def e = thrown(HttpClientResponseException)
            e.message == "Not Found"
            e.status == HttpStatus.NOT_FOUND

        cleanup:
            client.stop()
            client.close()
    }

    @Controller("/status")
    static class StatusController {

        @Get(uri = "/simple", produces = MediaType.TEXT_PLAIN)
        String simple() {
            return "success"
        }

        @Status(HttpStatus.CREATED)
        @Get(uri = "/simpleCreated", produces = MediaType.TEXT_PLAIN)
        String simpleCreated() {
            return "success"
        }

        @Status(HttpStatus.NOT_FOUND)
        @Get(uri = "/simple404", produces = MediaType.TEXT_PLAIN)
        String simple404() {
            return "success"
        }
    }
}