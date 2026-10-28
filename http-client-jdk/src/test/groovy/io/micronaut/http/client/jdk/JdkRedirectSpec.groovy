package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
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
            // replaces the default list, which includes Authorization
            'micronaut.http.client.redirect-cross-origin-filtered-headers': ['X-Api-Key'],
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
        response == 'X-Api-Key=none X-Other=other Authorization=none'
    }

    def 'Authorization is not copied to a cross-origin redirect, even if the configured headers do not include it'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/redirect?port=$otherServer.port")
                .header(HttpHeaders.AUTHORIZATION, 'Bearer secret'))

        then:
        response == 'X-Api-Key=none X-Other=none Authorization=none'
    }

    def '#header is not copied to a cross-origin redirect, even if the configured headers do not include it'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/redirect-header?port=$otherServer.port&name=$header")
                .header(header, value))

        then:
        response == 'none'

        where:
        header                | value
        HttpHeaders.COOKIE    | 'session=secret'
        // an Origin that is not localhost is rejected by the server
        HttpHeaders.ORIGIN    | 'http://localhost:1'
        HttpHeaders.REFERER   | 'http://localhost:1/secret'
    }

    def '#header is copied to a same-origin redirect'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/redirect-header?port=$server.port&name=$header")
                .header(header, value))

        then:
        response == value

        where:
        header                | value
        HttpHeaders.COOKIE    | 'session=secret'
        // an Origin that is not localhost is rejected by the server
        HttpHeaders.ORIGIN    | 'http://localhost:1'
        HttpHeaders.REFERER   | 'http://localhost:1/secret'
    }

    def 'a 303 redirect of a HEAD request stays HEAD'() {
        when:
        def response = client.toBlocking().exchange(HttpRequest.HEAD("/jdk-redirect/head-redirect"))

        then:
        response.status == HttpStatus.OK
        response.header('X-Method') == 'HEAD'
    }

    def 'a 301 redirect of a PUT request without a body keeps the method'() {
        expect:
        client.toBlocking().retrieve(HttpRequest.PUT("/jdk-redirect/empty-redirect", '')) == 'PUT'
    }

    def 'configured headers are copied to a same-origin redirect'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect/redirect?port=$server.port")
                .header('X-Api-Key', 'secret')
                .header('X-Other', 'other')
                .header(HttpHeaders.AUTHORIZATION, 'Bearer secret'))

        then:
        response == 'X-Api-Key=secret X-Other=other Authorization=Bearer secret'
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
        response.byteBody().buffer().get().toString(StandardCharsets.UTF_8) == 'X-Api-Key=none X-Other=other Authorization=none'

        cleanup:
        response?.close()
        rawClient?.close()
    }

    def 'a #status redirect of a #method request is followed with #expected'() {
        expect:
        client.toBlocking().retrieve(HttpRequest.create(HttpMethod.valueOf(method), "/jdk-redirect/body-redirect?status=$status").body('foo').contentType(MediaType.TEXT_PLAIN)) == expected

        where:
        status | method | expected
        307    | 'POST' | 'POST foo'
        308    | 'PUT'  | 'PUT foo'
        301    | 'PUT'  | 'PUT foo'
        302    | 'PUT'  | 'PUT foo'
        302    | 'POST' | 'GET'
        303    | 'POST' | 'GET'
        303    | 'PUT'  | 'GET'
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

        @Put('/empty-redirect')
        HttpResponse<?> emptyRedirect() {
            HttpResponse.status(HttpStatus.MOVED_PERMANENTLY).header(HttpHeaders.LOCATION, '/jdk-redirect/empty-target')
        }

        @Put(value = '/empty-target', produces = MediaType.TEXT_PLAIN)
        String emptyTarget(HttpRequest<?> request) {
            request.methodName
        }

        @Get('/redirect-header')
        HttpResponse<?> redirectHeader(@QueryValue int port, @QueryValue String name) {
            HttpResponse.redirect(URI.create("http://localhost:$port/jdk-redirect/echo-header?name=$name"))
        }

        @Get(value = '/echo-header', produces = MediaType.TEXT_PLAIN)
        String echoHeader(HttpRequest<?> request, @QueryValue String name) {
            request.headers.get(name) ?: 'none'
        }

        @Head('/head-redirect')
        HttpResponse<?> headRedirect() {
            HttpResponse.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, '/jdk-redirect/head-target')
        }

        @Head('/head-target')
        HttpResponse<?> headTarget(HttpRequest<?> request) {
            HttpResponse.ok().header('X-Method', request.methodName)
        }

        @Get(value = '/head-target', headRoute = false)
        HttpResponse<?> getTarget(HttpRequest<?> request) {
            HttpResponse.ok().header('X-Method', request.methodName)
        }

        @Get(value = '/echo', produces = MediaType.TEXT_PLAIN)
        String echo(HttpRequest<?> request) {
            "X-Api-Key=${request.headers.get('X-Api-Key') ?: 'none'} X-Other=${request.headers.get('X-Other') ?: 'none'} Authorization=${request.headers.get(HttpHeaders.AUTHORIZATION) ?: 'none'}"
        }

        @Post(value = '/body-redirect', consumes = MediaType.TEXT_PLAIN)
        HttpResponse<?> postRedirect(@QueryValue int status, @Body String body) {
            HttpResponse.status(HttpStatus.valueOf(status)).header(HttpHeaders.LOCATION, '/jdk-redirect/method')
        }

        @Put(value = '/body-redirect', consumes = MediaType.TEXT_PLAIN)
        HttpResponse<?> putRedirect(@QueryValue int status, @Body String body) {
            postRedirect(status, body)
        }

        @Post(value = '/method', consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String methodPost(@Body String body) {
            "POST $body"
        }

        @Put(value = '/method', consumes = MediaType.TEXT_PLAIN, produces = MediaType.TEXT_PLAIN)
        String methodPut(@Body String body) {
            "PUT $body"
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
