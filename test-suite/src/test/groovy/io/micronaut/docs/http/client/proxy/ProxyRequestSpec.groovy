package io.micronaut.docs.http.client.proxy

import io.micronaut.context.annotation.Property
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.exceptions.ReadTimeoutException
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject
import java.time.Duration

@MicronautTest
@Property(name = "micronaut.http.client.read-idle-timeout", value = "10s")
@Property(name = "micronaut.http.client.log-level", value = "TRACE")
class ProxyRequestSpec extends Specification {
    @Inject
    @Client("/")
    RxHttpClient client

    @Inject
    EmbeddedServer embeddedServer

    void "test proxy request with read timeout"() {
        given:"A client with no read timeout"
        def configuration = new DefaultHttpClientConfiguration()
        configuration.setReadTimeout(Duration.ofSeconds(-1))
        def client = new DefaultHttpClient(embeddedServer.URL, configuration)

        when:"A GET request is proxied"
        client.exchange("/proxy/timeout", String).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.message.contains("Read Timeout")

        cleanup:
        client.close()
    }

    void "test proxy GET request from filter"() {
        when:"A GET request is proxied"
        def response = client.exchange("/proxy/get", String).blockingFirst()

        then:
        response.header('X-My-Response-Header') == 'YYY'
        response.body() == 'good XXX'

        when:"A GET request with an error is requested"
        client.exchange("/proxy/error", String).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.response.header('X-My-Response-Header') == 'YYY'
        e.message == "Internal Server Error: Bad things happened"

        when:"A GET request with a 404"
        client.exchange("/proxy/notThere", String).blockingFirst()

        then:
        e = thrown(HttpClientResponseException)
        e.response.header('X-My-Response-Header') == 'YYY'
        e.message == "Page Not Found"
    }

    void "test proxy POST request from filter"() {
        when:"A POST request is proxied"
        def response = client.exchange(HttpRequest.POST("/proxy/post/text", "John").contentType(MediaType.TEXT_PLAIN), String).blockingFirst()

        then:
        response.header('X-My-Response-Header') == 'YYY'
        response.body() == 'Hello John XXX'

        when:"A POST request with no body is proxied"
        response = client.exchange(HttpRequest.POST("/proxy/post/nobody", "John").contentType(MediaType.TEXT_PLAIN), String).blockingFirst()

        then:
        response.status() == HttpStatus.NO_CONTENT

        when:"A POST request with JSON is proxied"
        response = client.exchange(HttpRequest.POST("/proxy/post/json", new Message(text: "John")).contentType(MediaType.APPLICATION_JSON), Message).blockingFirst()

        then:
        response.header('X-My-Response-Header') == 'YYY'
        response.body().text == 'Hello John XXX'

        when:"A POST request is sent with an invalid content type"
        response = client.exchange(HttpRequest.POST("/proxy/post/json", new Message(text: "John")).contentType(MediaType.TEXT_PLAIN), Message).blockingFirst()

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.UNSUPPORTED_MEDIA_TYPE
        e.message == 'Content Type [text/plain] not allowed. Allowed types: [application/json]'
    }

    @Controller("/real")
    static class TargetController {
        @Get("/get")
        @Produces(MediaType.TEXT_PLAIN)
        String index(HttpHeaders headers) {
            return "good " + headers.get("X-My-Request-Header")
        }

        @Get("/timeout")
        @Produces(MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.IO)
        String timeout() {
            sleep(15000)
            return "done"
        }

        @Post("/post/text")
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        String text(HttpHeaders headers, @Body String body) {
            return "Hello " + body +  " " + headers.get("X-My-Request-Header")
        }

        @Post("/post/nobody")
        @Produces(MediaType.TEXT_PLAIN)
        @Consumes(MediaType.TEXT_PLAIN)
        HttpResponse<?> noBody(HttpHeaders headers, @Body String body) {
            return HttpResponse.noContent()
        }

        @Post("/post/json")
        @Produces(MediaType.APPLICATION_JSON)
        @Consumes(MediaType.APPLICATION_JSON)
        Message json(HttpHeaders headers, @Body Message body) {
            return new Message(text:"Hello " + body.text +  " " + headers.get("X-My-Request-Header"))
        }

        @Get("/error")
        @Produces(MediaType.TEXT_PLAIN)
        String error() {
            throw new RuntimeException("Bad things happened")
        }
    }

    @Introspected
    static class Message { String text }
}
