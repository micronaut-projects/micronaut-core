/*
 * Copyright 2017 original authors
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
package org.particleframework.http.server.netty.sse

import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import org.particleframework.context.annotation.Requires
import org.particleframework.http.HttpResponse
import org.particleframework.http.server.netty.AbstractParticleSpec
import org.particleframework.http.sse.Event
import org.particleframework.http.sse.EventStream
import org.particleframework.stereotype.Controller
import org.particleframework.web.router.annotation.Get
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ServerSentEventSpec extends AbstractParticleSpec {


    void "test consume event stream"() {
        given:
        SseController.complete.set(false)
        MyEventHandler eventHandler = new MyEventHandler()
        EventSource eventSource = new EventSource.Builder(
            eventHandler, new URI("$server/sse/stream")
        ).reconnectTimeMs(1000*100).build()

        eventSource.start()
        while(!SseController.complete.get()) {
            sleep 100
        }

        expect:
        eventHandler.events.size() == 4
        eventHandler.events.first().data.data == '{"name":"Foo 1","age":11}'
    }

    @Controller
    @Requires(property = 'spec.name', value = 'ServerSentEventSpec')
    static class SseController {
        static AtomicBoolean complete = new AtomicBoolean(false)
        @Get
        EventStream stream() {
            EventStream.of { Subscriber<Event> eventSubscriber ->
                for(i in 1..4) {
                    eventSubscriber.onNext(Event.of(new Foo(name: "Foo $i", age: i + 10)))
                }
                eventSubscriber.onComplete()
                complete.set(true)
            }
        }
    }

    static class Foo {
        String name
        Integer age
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