package io.micronaut.docs.http.client.proxy

// tag::imports[]
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
// end::imports[]

// tag::class[]
@Filter("/proxy/**")
class ProxyFilter(
    private val client: ProxyHttpClient, // <2>
    private val embeddedServer: EmbeddedServer
) : HttpServerFilter { // <1>

    override fun doFilter(request: HttpRequest<*>,
                          chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return Publishers.map(client.proxy( // <3>
            request.mutate() // <4>
                .uri { b: UriBuilder -> // <5>
                    b.apply {
                        scheme("http")
                        host(embeddedServer.host)
                        port(embeddedServer.port)
                        replacePath(StringUtils.prependUri(
                            "/real",
                            request.path.substring("/proxy".length))
                        )
                    }
                }
                .header("X-My-Request-Header", "XXX") // <6>
        ), { response: MutableHttpResponse<*> -> response.header("X-My-Response-Header", "YYY") })
    }
}
