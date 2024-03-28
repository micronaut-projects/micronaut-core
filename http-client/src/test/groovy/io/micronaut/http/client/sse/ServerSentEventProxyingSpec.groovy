package io.micronaut.http.client.sse

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.ProxyHttpClient
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Inject
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

@Issue("https://github.com/micronaut-projects/micronaut-core/issues/6985")
class ServerSentEventProxyingSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['spec.name': 'ServerSentEventProxyingSpec']
    )

    @Shared
    @AutoCleanup
    SseClient sseClient = embeddedServer.applicationContext.createBean(SseClient, embeddedServer.getURL())

    void "test a request expecting a full response intercepted by a proxy providing a streaming response"() {
        given:
        def result = Flux.from(sseClient.eventStream("/proxy/events", String.class)).collectList().block()

        expect:
        result.data == ['EventNo: 1', 'EventNo: 2']
    }

    @Requires(property = "spec.name", value = "ServerSentEventProxyingSpec")
    @Controller
    @ExecuteOn(TaskExecutors.IO)
    static class TestController {

        @Get(value = "/real/events", produces = MediaType.TEXT_EVENT_STREAM)
        public Flux<Event<String>> events() {
            return Flux.range(1, 2)
                    .delayElements(Duration.ofSeconds(1))
                    .map(i -> Event.of("EventNo: " + i).name("TYPE1"));
        }
    }

    @Requires(property = "spec.name", value = "ServerSentEventProxyingSpec")
    @Filter("/proxy/**")
    static class TestFilter implements HttpServerFilter {

        @Inject
        ProxyHttpClient client
        @Inject
        EmbeddedServer server

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return client.proxy( //
                    request.mutate() //
                            .uri(b -> b //
                                    .scheme("http")
                                    .host(server.getHost())
                                    .port(server.getPort())
                                    .replacePath(StringUtils.prependUri(
                                            "/real",
                                            request.getPath().substring("/proxy".length())
                                    ))
                            )
            )
        }
    }
}
