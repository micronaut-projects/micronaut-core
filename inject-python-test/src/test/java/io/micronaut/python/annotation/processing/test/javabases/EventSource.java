/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test.javabases;

/**
 * Java methods taking the Java interface and the Java base class that Python classes defined
 * inside functions implement.
 */
public final class EventSource {

    private EventSource() {
    }

    public static String publish(EventSink<String> sink, String... events) {
        for (String event : events) {
            sink.onEvent(event);
        }
        sink.onComplete();
        return sink.describe();
    }

    public static String greet(GreetingBase greeter) {
        return greeter.describe();
    }

    public static String count(AbstractCounter counter) {
        return counter.next() + "," + counter.next();
    }
}
