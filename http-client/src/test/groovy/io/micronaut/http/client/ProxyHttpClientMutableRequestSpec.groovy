package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.*
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Produces
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Named
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import reactor.core.publisher.Flux
import spock.lang.Issue
import spock.lang.Specification

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN

class ProxyHttpClientMutableRequestSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/6073")
    void "ProxyHttpClient will mutate a request if necessary"() {
        given:
        int helloServerPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer helloEmbeddedServer = ApplicationContext.run(EmbeddedServer.class, [
                'micronaut.server.port': helloServerPort,
                'spec.name': 'ProxyHttpClientMutableRequestSpec.hello',
        ])
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer.class, [
                'spec.name': 'ProxyHttpClientMutableRequestSpec',
                'proxies.hello.url': "http://localhost:$helloServerPort".toString(),
        ])

        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        String result = client.retrieve(HttpRequest.GET('/hello/john').accept(MediaType.TEXT_PLAIN))

        then:
        'Hello john' == result

        when:
        result = client.retrieve(HttpRequest.POST('/hello/name', "Sally").contentType(MediaType.TEXT_PLAIN).accept(MediaType.TEXT_PLAIN))

        then:
        'Hello Sally' == result

        cleanup:
        helloEmbeddedServer.close()
        client.close()
        httpClient.close()
        embeddedServer.close()
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
            proxyHttpClient.proxy(request)
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
    }
}
