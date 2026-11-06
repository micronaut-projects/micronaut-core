package io.micronaut.http.client.sse

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.AsyncStreamingHttpClient
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

class ServerSentEventStateSpec extends Specification {

    static final EventStreamState REPLACEMENT = new EventStreamState()

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ServerSentEventStateSpec'])
    @Shared @AutoCleanup SseClient sseClient = embeddedServer.applicationContext.createBean(SseClient, embeddedServer.getURL())

    void "the state of the request follows a retry and an id of blocks without data"() {
        given:
        EventStreamState state = new EventStreamState()

        when:
        List<Event<String>> events = Flux.from(sseClient.eventStream(request("/stream/state", state), String)).collectList().block()

        then:
        events.collect { [it.data, it.id, it.retry] } == [["hi", null, null]]
        state.reconnectionTime == Duration.ofMillis(50)
        state.lastEventId == "9"
    }

    void "without a state the events are unchanged"() {
        when:
        List<Event<String>> events = Flux.from(sseClient.eventStream("/stream/state", String)).collectList().block()

        then:
        events.collect { [it.data, it.id, it.retry] } == [["hi", null, null]]
    }

    void "the state is that of the request after the filters"() {
        given:
        EventStreamState state = new EventStreamState()
        MutableHttpRequest<?> request = request("/stream/state", state).header("X-Replace-State", "true")

        when:
        Flux.from(sseClient.eventStream(request, String)).collectList().block()

        then:
        state.lastEventId == null
        REPLACEMENT.lastEventId == "9"
        REPLACEMENT.reconnectionTime == Duration.ofMillis(50)
    }

    void "the state follows a redirect"() {
        given:
        EventStreamState state = new EventStreamState()

        when:
        List<Event<String>> events = Flux.from(sseClient.eventStream(request("/stream/redirect", state), String)).collectList().block()

        then:
        events*.data == ["hi"]
        state.lastEventId == "9"
    }

    void "exchangeEventStream updates the state"() {
        given:
        EventStreamState state = new EventStreamState()

        when:
        List<HttpResponse<Event<String>>> responses = Flux.from(sseClient.exchangeEventStream(request("/stream/state", state), Argument.STRING)).collectList().block()

        then:
        responses*.body()*.data == ["hi"]
        state.lastEventId == "9"
        state.reconnectionTime == Duration.ofMillis(50)
    }

    void "the async eventStream updates the state"() {
        given:
        EventStreamState state = new EventStreamState()
        AsyncStreamingHttpClient client = ((io.micronaut.http.client.StreamingHttpClient) sseClient).toAsyncStreaming()

        when:
        def events = client.eventStream(request("/stream/state", state), Argument.STRING, Argument.STRING).toCompletableFuture().join()
        List<String> data = []
        events.forEach(e -> { data << e.data; java.util.concurrent.CompletableFuture.completedStage(null) }).toCompletableFuture().join()

        then:
        data == ["hi"]
        state.lastEventId == "9"
        state.reconnectionTime == Duration.ofMillis(50)
    }

    private static MutableHttpRequest<?> request(String uri, EventStreamState state) {
        MutableHttpRequest<?> request = HttpRequest.GET(uri)
        request.setAttribute(EventStreamState.ATTRIBUTE, state)
        return request
    }

    @Requires(property = 'spec.name', value = 'ServerSentEventStateSpec')
    @ClientFilter("/stream/**")
    static class ReplaceStateFilter {
        @RequestFilter
        void replace(MutableHttpRequest<?> request) {
            if (request.headers.contains("X-Replace-State")) {
                request.setAttribute(EventStreamState.ATTRIBUTE, REPLACEMENT)
            }
        }
    }

    @Requires(property = 'spec.name', value = 'ServerSentEventStateSpec')
    @Controller("/stream")
    static class StateController {

        @Get(value = '/state', produces = MediaType.TEXT_EVENT_STREAM)
        String state() {
            return "retry: 50\n\ndata: hi\n\nid: 9\n\n"
        }

        @Get(value = '/redirect', produces = MediaType.TEXT_EVENT_STREAM)
        HttpResponse<?> redirect() {
            return HttpResponse.redirect(URI.create('/stream/state'))
        }
    }
}
