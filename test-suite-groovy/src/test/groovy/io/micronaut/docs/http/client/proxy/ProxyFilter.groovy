package io.micronaut.docs.http.client.proxy

// tag::imports[]
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.filter.*
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher

// end::imports[]

// tag::class[]
@Filter("/proxy/**")
class ProxyFilter extends OncePerRequestHttpServerFilter { // <1>
    private final ProxyHttpClient client
    private final EmbeddedServer embeddedServer

    ProxyFilter(ProxyHttpClient client, EmbeddedServer embeddedServer) { // <2>
        this.client = client
        this.embeddedServer = embeddedServer
    }

    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        return Publishers.map(client.proxy( // <3>
                request.mutate() // <4>
                        .uri { UriBuilder b -> // <5>
                            b.with {
                                scheme("http")
                                host(embeddedServer.getHost())
                                port(embeddedServer.getPort())
                                replacePath(StringUtils.prependUri(
                                                "/real",
                                                request.getPath().substring("/proxy".length())
                                ))
                            }
                        }
                        .header("X-My-Request-Header", "XXX") // <6>
        ), { MutableHttpResponse<?> response -> response.header("X-My-Response-Header", "YYY")})
    }
}
// end::class[]
