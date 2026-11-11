package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.ByteBodyHttpResponse
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpMethod
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RawHttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets

/**
 * The redirects of the JDK client are followed like the JDK client follows them, as they always were,
 * unless {@code jdk.use-micronaut-redirects} is enabled: then the pipeline it shares with the Netty
 * client follows them, with the same behavior.
 */
class JdkRedirectSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                        : 'JdkRedirectSpec',
            'micronaut.server.port'            : -1,
            'micronaut.server.dualProtocol'    : true,
            'micronaut.server.ssl.enabled'     : true,
            'micronaut.server.ssl.buildSelfSigned': true,
            'micronaut.server.ssl.port'        : -1,
    ])

    @Shared
    @AutoCleanup
    ApplicationContext clientContext = ApplicationContext.run([
            'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
            'micronaut.http.client.jdk.use-micronaut-redirects'            : true,
    ])

    @Shared
    @AutoCleanup
    ApplicationContext defaultContext = ApplicationContext.run([
            'micronaut.http.client.ssl.insecure-trust-all-certificates': true,
    ])

    void "by default, a 301 or 302 redirect of a PUT is followed by the JDK client with a PUT with the body"() {
        given:
        HttpClient client = defaultContext.createBean(HttpClient, httpUrl())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.PUT("/jdk-redirect/put-$status", 'payload').contentType(MediaType.TEXT_PLAIN), String)

        then:
        response.body() == 'PUT payload'

        cleanup:
        client.close()

        where:
        status << [301, 302]
    }

    void "by default, a redirect loop returns the last redirect response"() {
        given:
        HttpClient client = defaultContext.createBean(HttpClient, httpUrl())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/jdk-redirect/loop'), String)

        then:
        response.code() == 301

        cleanup:
        client.close()
    }

    void "by default, a redirect from https to http is not followed"() {
        given:
        HttpClient client = defaultContext.createBean(HttpClient, httpsUrl())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/jdk-redirect/to-http?port=${httpPort()}"), String)

        then:
        response.code() == 301

        cleanup:
        client.close()
    }

    void "by default, a raw exchange follows redirects unless its options disable them"() {
        given:
        def raw = new JdkHttpClientFactory().createRawClient(httpUrl().toURI(), new io.micronaut.http.client.DefaultHttpClientConfiguration())

        when:
        HttpResponse<?> followed = reactor.core.publisher.Mono.from(raw.exchange(HttpRequest.GET('/jdk-redirect/to-content-type'), null, null)).block()
        HttpResponse<?> notFollowed = reactor.core.publisher.Mono.from(raw.exchange(HttpRequest.GET('/jdk-redirect/to-content-type'), null, null,
            io.micronaut.http.client.RawRequestOptions.builder().followRedirects(false).build())).block()

        then:
        followed.code() == 200
        notFollowed.code() == 301

        cleanup:
        followed?.close()
        notFollowed?.close()
        raw.close()
    }

    void "a 301 or 302 redirect of a PUT is followed with a GET without the body"() {
        given:
        HttpClient client = clientContext.createBean(HttpClient, httpUrl())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.PUT("/jdk-redirect/put-$status", 'payload').contentType(MediaType.TEXT_PLAIN), String)

        then:
        response.body() == 'GET without body'

        cleanup:
        client.close()

        where:
        status << [301, 302]
    }

    void "a 307 redirect of a PUT is followed with a PUT with the body"() {
        given:
        HttpClient client = clientContext.createBean(HttpClient, httpUrl())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.PUT('/jdk-redirect/put-307', 'payload').contentType(MediaType.TEXT_PLAIN), String)

        then:
        response.body() == 'PUT payload'

        cleanup:
        client.close()
    }

    void "a redirect loop fails once the maximum number of redirects is exceeded"() {
        given:
        HttpClient client = clientContext.createBean(HttpClient, httpUrl())

        when:
        client.toBlocking().exchange(HttpRequest.GET('/jdk-redirect/loop'), String)

        then:
        def e = thrown(HttpClientException)
        !(e instanceof HttpClientResponseException)
        e.message.contains('Maximum number of redirects exceeded')

        cleanup:
        client.close()
    }

    void "a redirect from https to http is followed"() {
        given:
        HttpClient client = clientContext.createBean(HttpClient, httpsUrl())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/jdk-redirect/to-http?port=${httpPort()}"), String)

        then:
        response.body() == 'plain http'

        cleanup:
        client.close()
    }

    void "the GET of a redirect has no content type"() {
        given:
        HttpClient client = clientContext.createBean(HttpClient, httpUrl())

        when:
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET('/jdk-redirect/to-content-type'), String)

        then:
        response.body() == 'no content type'

        cleanup:
        client.close()
    }

    void "a GET has no content type"() {
        given:
        HttpClient client = clientContext.createBean(HttpClient, httpUrl())

        expect:
        client.toBlocking().retrieve(HttpRequest.GET('/jdk-redirect/content-type')) == 'no content type'

        cleanup:
        client.close()
    }

    private URL httpUrl() {
        return new URL("http://localhost:${httpPort()}")
    }

    private URL httpsUrl() {
        return new URL("https://localhost:${server.port}")
    }

    private int httpPort() {
        return (server.boundPorts - server.port).first()
    }

    @Requires(property = 'spec.name', value = 'JdkRedirectSpec')
    @Controller('/jdk-redirect')
    static class RedirectController {

        @Put(uri = '/put-{status}')
        @Consumes(MediaType.ALL)
        HttpResponse<?> put(int status) {
            return HttpResponse.status(HttpStatus.valueOf(status)).headers { it.location(URI.create('/jdk-redirect/target')) }
        }

        @Get(uri = '/target', produces = MediaType.TEXT_PLAIN)
        String getTarget(HttpRequest<?> request) {
            return request.headers.contentLength().orElse(0L) == 0 ? 'GET without body' : 'GET with a body'
        }

        @Put(uri = '/target', produces = MediaType.TEXT_PLAIN)
        @Consumes(MediaType.ALL)
        String putTarget(@Body String body) {
            return 'PUT ' + body
        }

        @Get('/loop')
        HttpResponse<?> loop() {
            return HttpResponse.redirect(URI.create('/jdk-redirect/loop'))
        }

        @Get('/to-http')
        HttpResponse<?> toHttp(@QueryValue int port) {
            return HttpResponse.redirect(URI.create("http://localhost:$port/jdk-redirect/plain"))
        }

        @Get(uri = '/plain', produces = MediaType.TEXT_PLAIN)
        String plain(HttpRequest<?> request) {
            return request.secure ? 'https' : 'plain http'
        }

        @Get('/to-content-type')
        HttpResponse<?> toContentType() {
            return HttpResponse.redirect(URI.create('/jdk-redirect/content-type'))
        }

        @Get(uri = '/content-type', produces = MediaType.TEXT_PLAIN)
        String contentType(@Nullable @io.micronaut.http.annotation.Header(HttpHeaders.CONTENT_TYPE) String contentType) {
            return contentType == null ? 'no content type' : 'content type ' + contentType
        }
    }
}

/**
 * The redirects the JDK client follows by default do not copy the headers the configuration
 * filters, nor the credentials and cookies of the request, to another origin.
 */
class JdkRedirectHeadersSpec extends Specification {

    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name'                                               : 'JdkRedirectHeadersSpec',
            // replaces the default list, which includes Authorization
            'micronaut.http.client.redirect-cross-origin-filtered-headers': ['X-Api-Key'],
            'micronaut.http.client.max-redirects'                     : 3,
    ])

    @AutoCleanup
    EmbeddedServer otherServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'JdkRedirectHeadersSpec'])

    @AutoCleanup
    HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    def 'configured headers are not copied to a cross-origin redirect'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect-headers/redirect?port=$otherServer.port")
                .header('X-Api-Key', 'secret')
                .header('X-Other', 'other'))

        then:
        response == 'X-Api-Key=none X-Other=other Authorization=none'
    }

    def 'Authorization is not copied to a cross-origin redirect, even if the configured headers do not include it'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect-headers/redirect?port=$otherServer.port")
                .header(HttpHeaders.AUTHORIZATION, 'Bearer secret'))

        then:
        response == 'X-Api-Key=none X-Other=none Authorization=none'
    }

    def '#header is not copied to a cross-origin redirect, even if the configured headers do not include it'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect-headers/redirect-header?port=$otherServer.port&name=$header")
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
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect-headers/redirect-header?port=$server.port&name=$header")
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
        def response = client.toBlocking().exchange(HttpRequest.HEAD("/jdk-redirect-headers/head-redirect"))

        then:
        response.status == HttpStatus.OK
        response.header('X-Method') == 'HEAD'
    }

    def 'a 301 redirect of a PUT request without a body keeps the method'() {
        expect:
        client.toBlocking().retrieve(HttpRequest.PUT("/jdk-redirect-headers/empty-redirect", '')) == 'PUT'
    }

    def 'configured headers are copied to a same-origin redirect'() {
        when:
        def response = client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect-headers/redirect?port=$server.port")
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
        def response = Mono.from(rawClient.exchange(HttpRequest.GET("$server.URL/jdk-redirect-headers/redirect?port=$otherServer.port")
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
        client.toBlocking().retrieve(HttpRequest.create(HttpMethod.valueOf(method), "/jdk-redirect-headers/body-redirect?status=$status").body('foo').contentType(MediaType.TEXT_PLAIN)) == expected

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
        def response = client.toBlocking().exchange(HttpRequest.GET("/jdk-redirect-headers/loop?count=4"), String)

        then: 'like the JDK client, the response of the redirect beyond the maximum is returned'
        response.status == HttpStatus.MOVED_PERMANENTLY

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/jdk-redirect-headers/loop?count=3")) == 'done'
    }

    @Requires(property = 'spec.name', value = 'JdkRedirectHeadersSpec')
    @Controller('/jdk-redirect-headers')
    static class RedirectController {
        @Get('/redirect')
        HttpResponse<?> redirect(@QueryValue int port) {
            HttpResponse.redirect(URI.create("http://localhost:$port/jdk-redirect-headers/echo"))
        }

        @Put('/empty-redirect')
        HttpResponse<?> emptyRedirect() {
            HttpResponse.status(HttpStatus.MOVED_PERMANENTLY).header(HttpHeaders.LOCATION, '/jdk-redirect-headers/empty-target')
        }

        @Put(value = '/empty-target', produces = MediaType.TEXT_PLAIN)
        String emptyTarget(HttpRequest<?> request) {
            request.methodName
        }

        @Get('/redirect-header')
        HttpResponse<?> redirectHeader(@QueryValue int port, @QueryValue String name) {
            HttpResponse.redirect(URI.create("http://localhost:$port/jdk-redirect-headers/echo-header?name=$name"))
        }

        @Get(value = '/echo-header', produces = MediaType.TEXT_PLAIN)
        String echoHeader(HttpRequest<?> request, @QueryValue String name) {
            request.headers.get(name) ?: 'none'
        }

        @Head('/head-redirect')
        HttpResponse<?> headRedirect() {
            HttpResponse.status(HttpStatus.SEE_OTHER).header(HttpHeaders.LOCATION, '/jdk-redirect-headers/head-target')
        }

        @Head('/head-target')
        HttpResponse<?> headTarget(HttpRequest<?> request) {
            HttpResponse.ok().header('X-Method', request.methodName)
        }

        @Get('/head-target')
        HttpResponse<?> getTarget(HttpRequest<?> request) {
            HttpResponse.ok().header('X-Method', request.methodName)
        }

        @Get(value = '/echo', produces = MediaType.TEXT_PLAIN)
        String echo(HttpRequest<?> request) {
            "X-Api-Key=${request.headers.get('X-Api-Key') ?: 'none'} X-Other=${request.headers.get('X-Other') ?: 'none'} Authorization=${request.headers.get(HttpHeaders.AUTHORIZATION) ?: 'none'}"
        }

        @Post(value = '/body-redirect', consumes = MediaType.TEXT_PLAIN)
        HttpResponse<?> postRedirect(@QueryValue int status, @Body String body) {
            HttpResponse.status(HttpStatus.valueOf(status)).header(HttpHeaders.LOCATION, '/jdk-redirect-headers/method')
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
            count == 0 ? HttpResponse.ok('done') : HttpResponse.redirect(URI.create("/jdk-redirect-headers/loop?count=${count - 1}"))
        }
    }
}
