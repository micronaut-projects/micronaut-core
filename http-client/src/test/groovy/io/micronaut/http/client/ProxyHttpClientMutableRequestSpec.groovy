package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.*
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Named
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Specification

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN

class ProxyHttpClientMutableRequestSpec extends Specification {

    @AutoCleanup
    EmbeddedServer helloEmbeddedServer = ApplicationContext.run(EmbeddedServer.class, [
            'spec.name': 'ProxyHttpClientMutableRequestSpec.hello',
    ])

    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, [
            'spec.name': 'ProxyHttpClientMutableRequestSpec',
            'proxies.hello.url': "http://localhost:${helloEmbeddedServer.getPort()}".toString(),
    ])

    @AutoCleanup
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6073")
    void "ProxyHttpClient will mutate a request if necessary"() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        String result = client.retrieve(HttpRequest.GET('/hello/john').accept(MediaType.TEXT_PLAIN))

        then:
        'Hello john' == result

        when:
        result = client.retrieve(HttpRequest.POST('/hello/name', "Sally").contentType(MediaType.TEXT_PLAIN).accept(MediaType.TEXT_PLAIN))

        then:
        'Hello Sally' == result

        when:
        result = client.retrieve(HttpRequest.GET('/hello/host').accept(MediaType.TEXT_PLAIN))

        then:
        result == "Host: $helloEmbeddedServer.host:$helloEmbeddedServer.port"

        when:'https://github.com/micronaut-projects/micronaut-core/issues/7158'
        result = client.retrieve(HttpRequest.GET('/hello/host-update').accept(MediaType.TEXT_PLAIN))
        then:
        result == "Host: foo"
    }

    @Requires(property = 'spec.name', value = 'ProxyHttpClientMutableRequestSpec')
    @EachProperty("proxies")
    static class ProxyConfig {
        private final String name
        URI url
        ProxyConfig(@Parameter String name) {
            this.name = name;
        }

        String getName() {
            this.name
        }
    }
    @Requires(property = 'spec.name', value = 'ProxyHttpClientMutableRequestSpec')
    @Factory
    static class ProxyClientFactory {
        @EachBean(ProxyConfig)
        ProxyHttpClient create(ProxyConfig config) {
            ProxyHttpClient.create(config.getUrl().toURL())
        }
    }

    @Requires(property = 'spec.name', value = 'ProxyHttpClientMutableRequestSpec')
    @Filter(MATCH_ALL_PATTERN)
    static class ApiGatewayFilter implements HttpFilter {
        private final ProxyHttpClient proxyHttpClient
        ApiGatewayFilter(@Named("hello") ProxyHttpClient proxyHttpClient) {
            this.proxyHttpClient = proxyHttpClient
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
            if (request.path == "/hello/host-update") {
                def mutableReq = request.mutate()
                        .uri(UriBuilder.of(request.uri).replacePath("/hello/host").build())
                mutableReq.getHeaders().set("Host", "foo")
                return proxyHttpClient.proxy(mutableReq, ProxyRequestOptions.builder().retainHostHeader().build())
            } else {
                return proxyHttpClient.proxy(request)
            }
        }
    }

    @Requires(property = 'spec.name', value = 'ProxyHttpClientMutableRequestSpec.hello')
    @Controller("/hello")
    static class HelloWorldController {

        @Produces(MediaType.TEXT_PLAIN)
        @Get("/john")
        String john() {
            "Hello john"
        }

        @Post(uri = "/name", processes = MediaType.TEXT_PLAIN)
        Publisher<String> name(@Body Publisher<String> name) {
            Flux.from(name).map(str -> "Hello " + str)
        }

        @Get(uri = "/host", processes = MediaType.TEXT_PLAIN)
        String host(@Header String host) {
            "Host: $host"
        }
    }
}
