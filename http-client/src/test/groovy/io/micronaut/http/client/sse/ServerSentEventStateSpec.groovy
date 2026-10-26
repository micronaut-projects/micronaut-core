package io.micronaut.http.client.sse

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration

class ServerSentEventStateSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'ServerSentEventStateSpec'])
    @Shared @AutoCleanup SseClient sseClient = embeddedServer.applicationContext.createBean(SseClient, embeddedServer.getURL())

    void "the state of the request follows a retry and an id of blocks without data"() {
        given:
        EventStreamState state = new EventStreamState()
        HttpRequest<?> request = HttpRequest.GET("/stream/state").setAttribute(EventStreamState.ATTRIBUTE, state)

        when:
        List<Event<String>> events = Flux.from(sseClient.eventStream(request, String)).collectList().block()

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

    @Requires(property = 'spec.name', value = 'ServerSentEventStateSpec')
    @Controller("/stream")
    static class StateController {

        @Get(value = '/state', produces = MediaType.TEXT_EVENT_STREAM)
        String state() {
            return "retry: 50\n\ndata: hi\n\nid: 9\n\n"
        }
    }
}
