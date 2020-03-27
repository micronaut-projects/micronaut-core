package io.micronaut.docs.server.sse


import io.micronaut.http.HttpRequest
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.sse.RxSseClient
import io.micronaut.http.sse.Event
import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Inject

@MicronautTest
class HeadlineControllerSpec extends Specification {

    @Inject
    @Client('/')
    RxSseClient client

    void "test consume event stream object"() {
        given:
        List<Event<Headline>> events = []

        client.eventStream(HttpRequest.GET("/headlines"), Headline).subscribe { event ->
            events.add(event)
        }

        PollingConditions conditions = new PollingConditions(timeout: 3)
        expect:
        conditions.eventually {
            events.size() == 2
            events[0].data.title == "Micronaut 1.0 Released"
            events[0].data.description == "Come and get it"
        }


    }
}