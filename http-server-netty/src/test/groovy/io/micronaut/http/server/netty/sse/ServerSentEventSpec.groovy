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
package io.micronaut.http.server.netty.sse

import com.launchdarkly.eventsource.EventHandler
import com.launchdarkly.eventsource.EventSource
import com.launchdarkly.eventsource.MessageEvent
import io.reactivex.Flowable
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.server.netty.AbstractMicronautSpec
import io.micronaut.http.sse.Event
import io.micronaut.http.annotation.Get
import org.reactivestreams.Publisher

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ServerSentEventSpec extends AbstractMicronautSpec {


    void "test consume event stream object"() {
        given:
        SseController.complete.set(false)
        MyEventHandler eventHandler = new MyEventHandler()
        EventSource eventSource = new EventSource.Builder(
                eventHandler, new URI("$server/sse/object")
        ).reconnectTimeMs(1000 * 100).build()

        eventSource.start()
        while (!SseController.complete.get()) {
            sleep 100
        }

        expect:
        eventHandler.events.size() == 4
        eventHandler.events.first().data.data == '{"name":"Foo 1","age":11}'

        cleanup:
        eventSource.close()
    }

    void "test consume event stream string"() {
        given:
        SseController.complete.set(false)
        MyEventHandler eventHandler = new MyEventHandler()
        EventSource eventSource = new EventSource.Builder(
                eventHandler, new URI("$server/sse/string")
        ).reconnectTimeMs(1000 * 100).build()

        eventSource.start()
        while (!SseController.complete.get()) {
            sleep 100
        }

        expect:
        eventHandler.events.size() == 4
        eventHandler.events.first().data.data == 'Foo 1'

        cleanup:
        eventSource.close()
    }

    void "test consume rich event stream object"() {
        given:
        SseController.complete.set(false)
        MyEventHandler eventHandler = new MyEventHandler()
        EventSource eventSource = new EventSource.Builder(
                eventHandler, new URI("$server/sse/rich")
        ).reconnectTimeMs(1000 * 100).build()

        when:
        eventSource.start()
        while (!SseController.complete.get()) {
            sleep 100
        }

        then:
        eventHandler.events.size() == 4
        eventHandler.comments.size() == 4

        when:
        Event event = eventHandler.events.first()

        then:
        event.name == 'foo'

        cleanup:
        eventSource.close()
    }

    void "test receive error from supplier"() {
        given:
        SseController.complete.set(false)
        MyEventHandler eventHandler = new MyEventHandler()
        EventSource eventSource = new EventSource.Builder(
                eventHandler, new URI("$server/sse/exception")
        ).reconnectTimeMs(1000 * 100).build()

        when:
        eventSource.start()
        while (!SseController.complete.get()) {
            sleep 100
        }

        then:
        eventHandler.events.size() == 0
        eventHandler.comments.size() == 0

        cleanup:
        eventSource.close()
    }

    void "test receive error from onError"() {
        given:
        SseController.complete.set(false)
        MyEventHandler eventHandler = new MyEventHandler()
        EventSource eventSource = new EventSource.Builder(
                eventHandler, new URI("$server/sse/onError")
        ).reconnectTimeMs(1000 * 100).build()

        when:
        eventSource.start()
        while (!SseController.complete.get()) {
            sleep 100
        }

        then:
        eventHandler.events.size() == 0
        eventHandler.comments.size() == 0

        cleanup:
        eventSource.close()
    }

    @Controller
    @Requires(property = 'spec.name', value = 'ServerSentEventSpec')
    static class SseController {
        static AtomicBoolean complete = new AtomicBoolean(false)

        @Get
        Publisher<Event> object() {
            int i = 0
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                if (i < 4) {
                    i++
                    emitter.onNext(Event.of(new Foo(name: "Foo $i", age: i + 10)))
                }
                else {
                    emitter.onComplete()
                    complete.set(true)
                }
            })
        }

        @Get
        Publisher<Event> rich() {
            Integer i = 0
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                if (i < 4) {
                    i++
                    emitter.onNext(
                    Event.of(new Foo(name: "Foo $i", age: i + 10))
                            .name('foo')
                            .id(i.toString())
                            .comment("Foo Comment $i")
                            .retry(Duration.of(2, ChronoUnit.MINUTES)))
                }
                else {
                    emitter.onComplete()
                    complete.set(true)
                }
            })
        }

        @Get
        Publisher<Event> string() {
            int i = 0
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                if (i < 4) {
                    i++
                    emitter.onNext(Event.of("Foo $i"))
                }
                else {
                    emitter.onComplete()
                    complete.set(true)
                }
            })
        }

        @Get
        Publisher<Event> exception() {
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                complete.set(true)
                throw new RuntimeException("bad things happened")
            })
        }

        @Get
        Publisher<Event> onError() {
            Flowable.generate( { io.reactivex.Emitter<Event> emitter ->
                emitter.onError(new RuntimeException("bad things happened"))
                complete.set(true)
            })
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