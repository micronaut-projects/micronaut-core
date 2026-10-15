package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The redirects of the JDK client are followed by the JDK client itself, as they always were,
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
