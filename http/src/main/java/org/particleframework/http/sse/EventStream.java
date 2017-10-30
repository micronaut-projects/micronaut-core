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
package org.particleframework.http.sse;

import org.reactivestreams.Subscriber;

import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Creates a Server Sent Event (SSE) stream
 *
 * TODO: Change this to implement Publisher
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface EventStream extends Consumer<Subscriber<? super Event>>  {

    /**
     * A stream for the given consumer. The consumer accepts a {@link Subscriber} with which the API implementer can send events
     *
     * @param subscriber The subscriber
     * @return The EventStream
     */
    static EventStream of(Consumer<Subscriber<? super Event>> subscriber) {
        return subscriber::accept;
    }

    /**
     * Creates a stream from the given objects
     *
     * @param objects The objects
     * @return The Stream
     */
    static EventStream of(Object...objects) {
        return of( subscriber -> {
            for (Object object : objects) {
                if(object instanceof Event) {
                    subscriber.onNext((Event) object);
                }
                else {
                    subscriber.onNext(Event.of(object));
                }
            }
            subscriber.onComplete();
        });
    }

    /**
     * Creates a event stream from the given Java stream
     *
     * @param stream The Java stream
     * @return The Stream
     */
    static EventStream of(Stream<?> stream) {
        return of(subscriber -> {
            stream.forEach( object -> {
                if(object instanceof Event) {
                    subscriber.onNext((Event) object);
                }
                else {
                    subscriber.onNext(Event.of(object));
                }
            });
            subscriber.onComplete();
        });
    }
}
