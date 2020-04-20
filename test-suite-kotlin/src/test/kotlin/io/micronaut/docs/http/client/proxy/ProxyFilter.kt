package io.micronaut.docs.http.client.proxy

// tag::imports[]
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.core.util.StringUtils
import io.micronaut.http.*
import io.micronaut.http.annotation.Filter
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.filter.*
import io.micronaut.http.uri.UriBuilder
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import java.util.function.Function
// end::imports[]

// tag::class[]
@Filter("/proxy/**")
class ProxyFilter(
        private val client: ProxyHttpClient, // <2>
        private val embeddedServer: EmbeddedServer) : OncePerRequestHttpServerFilter() { // <1>
    override fun doFilterOnce(request: HttpRequest<*>, chain: ServerFilterChain): Publisher<MutableHttpResponse<*>> {
        return Publishers.map(client.proxy( // <3>
                request.mutate() // <4>
                        .uri { b: UriBuilder -> // <5>
                            b.apply {
                                scheme("http")
                                host(embeddedServer.host)
                                port(embeddedServer.port)
                                replacePath(StringUtils.prependUri(
                                        "/real",
                                        request.path.substring("/proxy".length)
                                ))
                            }
                        }
                        .header("X-My-Request-Header", "XXX") // <6>
        ), Function { response: MutableHttpResponse<*> -> response.header("X-My-Response-Header", "YYY") })
    }

}