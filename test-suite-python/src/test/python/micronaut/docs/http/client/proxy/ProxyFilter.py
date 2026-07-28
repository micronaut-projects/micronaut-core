# tag::imports[]
import java

from micronaut.http import HttpRequest, MutableHttpResponse
from micronaut.http.annotation import Filter
from micronaut.http.client import ProxyHttpClient
from micronaut.http.filter import HttpServerFilter, ServerFilterChain
from micronaut.runtime.server import EmbeddedServer

Publisher = java.type("org.reactivestreams.Publisher")
Publishers = java.type("io.micronaut.core.async.publisher.Publishers")
StringUtils = java.type("io.micronaut.core.util.StringUtils")
# end::imports[]


# tag::class[]
@Filter("/proxy/**")
class ProxyFilter(HttpServerFilter):  # <1>
    def __init__(
        self,
        client: ProxyHttpClient,
        embeddedServer: EmbeddedServer,
    ) -> None:  # <2>
        self.client = client
        self.embeddedServer = embeddedServer

    def doFilter(
        self,
        request: HttpRequest,
        chain: ServerFilterChain,
    ) -> Publisher:
        proxy_request = (
            request.mutate()  # <4>
            .uri(
                lambda b: b
                .scheme("http")
                .host(self.embeddedServer.getHost())
                .port(self.embeddedServer.getPort())
                .replacePath(  # <5>
                    StringUtils.prependUri(
                        "/real",
                        request.getPath()[len("/proxy"):],
                    )
                )
            )
            .header("X-My-Request-Header", "XXX")  # <6>
        )
        return Publishers.map(  # <3>
            self.client.proxy(proxy_request),
            lambda response: response.header("X-My-Response-Header", "YYY"),
        )
# end::class[]
