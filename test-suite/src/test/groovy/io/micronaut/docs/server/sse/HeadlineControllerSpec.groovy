/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.server.sse

import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import io.micronaut.context.ApplicationContext
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class HeadlineControllerSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer =
            ApplicationContext.run(EmbeddedServer)

    void "test consume event stream object"() {
        given:
        MyEventHandler eventHandler = new MyEventHandler()
        EventSource eventSource = new EventSource.Builder(
                eventHandler, new URI( "${embeddedServer.URL}/headlines")
        ).reconnectTimeMs(1000 * 100).build()

        eventSource.start()

        PollingConditions conditions = new PollingConditions(timeout: 3)
        expect:
        conditions.eventually {
            eventHandler.events.size() == 2
            eventHandler.events.first().data.data == '{"title":"Micronaut 1.0 Released","description":"Come and get it"}'
        }

        cleanup:
        eventSource.close()
    }

}

class MyEventHandler implements EventHandler {
    boolean opened = false
    boolean closed = false
    List<String> comments = []
    List<Event<MessageEvent>> events = []
    List<Throwable> errors = []

    @Override
    void onOpen() throws Exception {
        opened = true
    }

    @Override
    void onClosed() throws Exception {
        closed = true
    }

    @Override
    void onMessage(String event, MessageEvent messageEvent) throws Exception {
        events.add(Event.of(messageEvent).name(event))
    }

    @Override
    void onComment(String comment) throws Exception {
        comments.add(comment)
    }

    @Override
    void onError(Throwable t) {
        errors.add(t)
    }
}