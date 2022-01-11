package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.filter.FilterChain
import io.micronaut.http.filter.HttpFilter
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Named
import org.reactivestreams.Publisher
import spock.lang.Issue
import spock.lang.Specification

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN

class ProxyHttpClientHostHeaderSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/3759")
    void "ProxyHttpClient host header can be controlled when hostMode is #configDesc"() {
        given:
        EmbeddedServer hostEchoingServer = ApplicationContext.run(EmbeddedServer.class, [
                'spec.name': 'ProxyHttpClientHostHeaderSpec.host',
        ])

        EmbeddedServer embeddedServer = ApplicationContext.run(
                EmbeddedServer.class,
                ['spec.name'       : 'ProxyHttpClientHostHeaderSpec',
                 'proxies.host.url': "http://localhost:${hostEchoingServer.getPort()}".toString(),
                ] + (config ? ['micronaut.http.client.proxy.hostMode': config,] : [:])
        )

        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        Map result = client.retrieve(HttpRequest.GET('/host').accept(MediaType.APPLICATION_JSON), Map)

        then:
        result == expected(embeddedServer, hostEchoingServer)

        cleanup:
        hostEchoingServer.close()
        client.close()
        httpClient.close()
        embeddedServer.close()

        where:
        config             | expected
        null               | { proxy, target -> [host: "$target.host:$target.port"] } // Behaves as replace
        'replace'          | { proxy, target -> [host: "$target.host:$target.port"] }
        'keep'             | { proxy, target -> [host: "$proxy.host:$proxy.port"] }
        'x_forwarded_host' | { proxy, target -> [host: "$target.host:$target.port", 'x-forwarded': "$proxy.host:$proxy.port"] }
        'forwarded'        | { proxy, target -> [host: "$target.host:$target.port", 'forwarded': "host=$proxy.host:$proxy.port"] }

        configDesc = config ?: 'unset'
    }

    void "ProxyHttpClient Forwarded mode appends to the header"() {
        given:
        EmbeddedServer hostEchoingServer = ApplicationContext.run(EmbeddedServer.class, [
                'spec.name': 'ProxyHttpClientHostHeaderSpec.host',
        ])

        EmbeddedServer embeddedServer = ApplicationContext.run(
                EmbeddedServer.class,
                ['spec.name'       : 'ProxyHttpClientHostHeaderSpec',
                 'proxies.host.url': "http://localhost:${hostEchoingServer.getPort()}".toString(),
                 'micronaut.http.client.proxy.hostMode': 'forwarded'
                ]
        )

        HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)
        BlockingHttpClient client = httpClient.toBlocking()

        when:
        Map result = client.retrieve(
                HttpRequest.GET('/host')
                        .accept(MediaType.APPLICATION_JSON)
                        .header("Forwarded", "host=some.previous.host"),
                Map
        )

        then:
        result == [
                host: "$hostEchoingServer.host:$hostEchoingServer.port",
                forwarded: "host=some.previous.host,host=$embeddedServer.host:$embeddedServer.port"
        ]

        cleanup:
        hostEchoingServer.close()
        client.close()
        httpClient.close()
        embeddedServer.close()
    }

    @Requires(property = 'spec.name', value = 'ProxyHttpClientHostHeaderSpec')
    @EachProperty("proxies")
    static class ProxyConfig {

        final String name
        URI url

        ProxyConfig(@Parameter String name) {
            this.name = name;
        }
    }

    @Requires(property = 'spec.name', value = 'ProxyHttpClientHostHeaderSpec')
    @Factory
    static class ProxyClientFactory {
        @EachBean(ProxyConfig)
        ProxyHttpClient create(HttpClientConfiguration clientConfig, ProxyConfig config) {
            ProxyHttpClient.create(config.url.toURL(), clientConfig)
        }
    }

    @Requires(property = 'spec.name', value = 'ProxyHttpClientHostHeaderSpec')
    @Filter(MATCH_ALL_PATTERN)
    static class ApiGatewayFilter implements HttpFilter {
        private final ProxyHttpClient proxyHttpClient

        ApiGatewayFilter(@Named("host") ProxyHttpClient proxyHttpClient) {
            this.proxyHttpClient = proxyHttpClient
        }

        @Override
        Publisher<? extends HttpResponse<?>> doFilter(HttpRequest<?> request, FilterChain chain) {
            proxyHttpClient.proxy(request)
        }
    }

    @Requires(property = 'spec.name', value = 'ProxyHttpClientHostHeaderSpec.host')
    @Controller("/host")
    static class HelloWorldController {

        @Get
        Map<String, String> name(
                @Nullable @Header(name = "host") String host,
                @Nullable @Header(name = "x-forwarded-host") String xForwardedHost,
                @Nullable @Header(name = "forwarded") String forwarded
        ) {
            [host: host, 'x-forwarded': xForwardedHost, forwarded: forwarded]
        }
    }

}
