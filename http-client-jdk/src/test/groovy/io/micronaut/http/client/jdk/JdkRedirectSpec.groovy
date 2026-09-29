package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class JdkRedirectSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                               : 'JdkRedirectSpec',
            'micronaut.http.client.redirect-cross-origin-filtered-headers': ['Authorization', 'X-Api-Key'],
            'micronaut.http.client.max-redirects'                     : 3,
    ])

    @AutoCleanup
    EmbeddedServer otherServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkRedirectSpec'])

    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    def 'configured headers are not copied to a cross-origin redirect'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/redirect?port=$otherServer.port")
                .header('X-Api-Key', 'secret')
                .header('X-Other', 'other'))

        then:
        response == 'X-Api-Key=none X-Other=other'
    }

    def 'configured headers are copied to a same-origin redirect'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/redirect?port=$server.port")
                .header('X-Api-Key', 'secret')
                .header('X-Other', 'other'))

        then:
        response == 'X-Api-Key=secret X-Other=other'
    }

    def 'the raw client does not copy configured headers to a cross-origin redirect'() {
        given:
        RawHttpClient rawClient = server.applicationContext.createBean(RawHttpClient)

        when:
        def response = Mono.from(rawClient.exchange(HttpRequest.GET("$server.URL/jdk-redirect/redirect?port=$otherServer.port")
                .header('X-Api-Key', 'secret')
                .header('X-Other', 'other'), null, null)).cast(ByteBodyHttpResponse).block()

        then:
        response.status == HttpStatus.OK
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == 'X-Api-Key=none X-Other=other'

        cleanup:
        response?.close()
        rawClient?.close()
    }

    def 'a 307 redirect keeps the method and body, a 303 redirect switches to GET'() {
        expect:
        client.toBlocking().retrieve(HttpRequest.POST("/jdk-redirect/post-redirect?status=307", 'foo').contentType(MediaType.TEXT_PLAIN)) == 'POST foo'
        client.toBlocking().retrieve(HttpRequest.POST("/jdk-redirect/post-redirect?status=303", 'foo').contentType(MediaType.TEXT_PLAIN)) == 'GET'
    }

    def 'the maximum number of redirects is honoured'() {
        when:
        client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/loop?count=4"))

        then:
        def e = thrown(HttpClientException)
        e.message.contains('Maximum number of redirects exceeded')

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/loop?count=3")) == 'done'
    }

    @Requires(property = 'spec.name', value = 'JdkRedirectSpec')
    @Controller('/jdk-redirect')
    static class RedirectController {
        @Get('/redirect')
        HttpResponse<?> redirect(@QueryValue int port) {
            HttpResponse.redirect(URI.create("http://localhost:$port/jdk-redirect/echo"))
        }

        @Get(value = '/echo', produces = MediaType.TEXT_PLAIN)
        String echo(HttpRequest<?> request) {
            "X-Api-Key=${request.headers.get('X-Api-Key') ?: 'none'} X-Other=${request.headers.get('X-Other') ?: 'none'}"
        }

        @Post(value = '/post-redirect', consumes = MediaType.TEXT_PLAIN)
        HttpResponse<?> postRedirect(@QueryValue int status, @Body String body) {
            HttpResponse.status(HttpStatus.valueOf(status)).header(HttpHeaders.LOCATION, '/jdk-redirect/method')
        }

        @Post(value = '/method', consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String methodPost(@Body String body) {
            "POST $body"
        }

        @Get(value = '/method', produces = MediaType.TEXT_PLAIN)
        String methodGet() {
            'GET'
        }

        @Get(value = '/loop', produces = MediaType.TEXT_PLAIN)
        HttpResponse<?> loop(@QueryValue int count) {
            count == 0 ? HttpResponse.ok('done') : HttpResponse.redirect(URI.create("/jdk-redirect/loop?count=${count - 1}"))
        }
    }
}
