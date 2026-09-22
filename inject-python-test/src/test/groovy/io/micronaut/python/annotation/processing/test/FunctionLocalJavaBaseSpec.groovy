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
package io.micronaut.python.annotation.processing.test

import io.micronaut.context.ApplicationContext
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

/**
 * Python classes defined inside a function body (a factory function, a test method) that extend an
 * imported Java interface or Java class: they have no generated Java class, so GraalPy's host
 * adapter implements them and their instances are Java objects of the base that any Java method,
 * overloaded or not, accepts.
 */
class FunctionLocalJavaBaseSpec extends AbstractPythonTypeElementSpec {

    void "a class defined inside a method implements a generic Java interface of a library passed to an overloaded method"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from org.reactivestreams import Subscriber, Subscription
from reactor.core.publisher import Mono


@Singleton
class PublishSpec:

    @Executable
    def run(self) -> str:
        counts = {"success": 0, "error": 0, "next": 0}

        class AcknowledgementSubscriber(Subscriber):
            def onSubscribe(self, subscription: Subscription) -> None:
                subscription.request(1)

            def onNext(self, item: object) -> None:
                counts["next"] += 1

            def onError(self, throwable: Exception) -> None:
                counts["error"] += 1

            def onComplete(self) -> None:
                counts["success"] += 1

        subscriber = AcknowledgementSubscriber()
        Mono.just("body").subscribe(subscriber)
        Mono.empty().subscribe(AcknowledgementSubscriber())
        return f"{counts['next']},{counts['success']},{counts['error']},{isinstance(subscriber, AcknowledgementSubscriber)},{isinstance(subscriber, Subscriber)}"
''', true)

        expect: 'Mono.subscribe(Subscriber) is selected among the Consumer and CoreSubscriber overloads'
        ctx.getBean(ctx.classLoader.loadClass('python.PublishSpec')).run() == '1,2,0,True,True'

        cleanup:
        ctx?.close()
    }

    void "a class defined inside a function implements an imported Java interface with default methods"() {
        given:
        ApplicationContext ctx = buildContext('''
from micronaut.python.annotation.processing.test.javabases import EventSink, EventSource


def publish(*events: str) -> list:
    received = []

    class CollectingSink(EventSink):
        def onEvent(self, event: str) -> None:
            received.append(event)

        def onComplete(self) -> None:
            received.append("done")

        def summary(self) -> str:
            return "+".join(received)

    sink = CollectingSink()
    sink.tag = "local"
    received.append(EventSource.publish(sink, *events))
    received.append(sink.summary())
    received.append(sink.tag)
    return received


def describe() -> str:
    class DescribedSink(EventSink):
        def onEvent(self, event: str) -> None:
            pass

        def onComplete(self) -> None:
            pass

        def describe(self) -> str:
            return "described:" + super().describe()

    sink = DescribedSink()
    return sink.describe() + "|" + EventSource.publish(sink)
''', true)
        Context polyglot = ctx.getBean(Context)

        when:
        def received = polyglot.eval('python', 'publish("a", "b")')

        then: 'the Java caller reaches the Python methods, the inherited default method and the Python attributes stay reachable'
        strings(received) == ['a', 'b', 'done', 'sink', 'a+b+done+sink', 'local']

        and: 'a default method overridden in Python is what Java and Python reach, and super() reaches the default'
        polyglot.eval('python', 'describe()').asString() == 'described:sink|described:sink'

        cleanup:
        ctx?.close()
    }

    void "a class defined inside a method implements a parameterized Java interface"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.python.annotation.processing.test.javabases import EventSink, EventSource
from org.reactivestreams import Subscriber, Subscription
from reactor.core.publisher import Mono


@Singleton
class TypedPublishSpec:

    @Executable
    def run(self) -> str:
        received = []

        class TypedSubscriber(Subscriber[str]):
            def onSubscribe(self, subscription: Subscription) -> None:
                subscription.request(1)

            def onNext(self, item: str) -> None:
                received.append(item)

            def onError(self, throwable: Exception) -> None:
                received.append("error")

            def onComplete(self) -> None:
                received.append("done")

        class TypedSink(EventSink[str]):
            def onEvent(self, event: str) -> None:
                received.append(event)

            def onComplete(self) -> None:
                received.append("complete")

        subscriber = TypedSubscriber()
        Mono.just("body").subscribe(subscriber)
        received.append(EventSource.publish(TypedSink(), "a"))
        return ",".join(received) + f"|{isinstance(subscriber, Subscriber)}"
''', true)

        expect: 'the type argument is dropped from the run time base, the raw interface being what the host adapter implements'
        ctx.getBean(ctx.classLoader.loadClass('python.TypedPublishSpec')).run() == 'body,done,a,complete,sink|True'

        cleanup:
        ctx?.close()
    }

    void "a dataclass defined inside a function stays a plain Python object implementing the interface"() {
        given:
        ApplicationContext ctx = buildContext('''
from dataclasses import dataclass

from micronaut.python.annotation.processing.test.javabases import EventSink, EventSource


def publish(*events: str) -> list:
    received = []

    @dataclass
    class PrefixSink(EventSink):
        prefix: str

        def onEvent(self, event: str) -> None:
            received.append(self.prefix + event)

        def onComplete(self) -> None:
            received.append(self.prefix + "done")

    sink = PrefixSink("p:")
    received.append(EventSource.publish(sink, *events))
    received.append(sink.describe())
    received.append(type(sink).__name__)
    return received
''', true)
        Context polyglot = ctx.getBean(Context)

        expect: 'the generated constructor takes the field, so the interface is stripped and a proxy of it reaches the Python object'
        strings(polyglot.eval('python', 'publish("a")')) == ['p:a', 'p:done', 'sink', 'sink', 'PrefixSink']

        cleanup:
        ctx?.close()
    }

    void "a class defined inside a function with constructor parameters stays a plain Python object implementing the interface"() {
        given:
        ApplicationContext ctx = buildContext('''
from micronaut.python.annotation.processing.test.javabases import EventSink, EventSource


def make_sink(events: list) -> EventSink:
    class CountingSink(EventSink):
        def __init__(self, events):
            self.events = events

        def onEvent(self, event: str) -> None:
            self.events.append(event)

        def onComplete(self) -> None:
            self.events.append("done")

    return CountingSink(events)


def publish(*events: str) -> list:
    received = []
    sink = make_sink(received)
    received.append(EventSource.publish(sink, *events))
    received.append(sink.describe())
    received.append(type(sink).__name__)
    return received
''', true)
        Context polyglot = ctx.getBean(Context)

        expect: 'the interface is stripped and a proxy of it reaches the Python object; the default method is installed on the class'
        strings(polyglot.eval('python', 'publish("a")')) == ['a', 'done', 'sink', 'sink', 'CountingSink']

        cleanup:
        ctx?.close()
    }

    void "a class defined inside a function extends an imported Java class"() {
        given:
        ApplicationContext ctx = buildContext('''
from micronaut.python.annotation.processing.test.javabases import AbstractCounter, EventSource, GreetingBase


def count() -> str:
    class LocalCounter(AbstractCounter):
        def label(self) -> str:
            return "local"

    return EventSource.count(LocalCounter("n"))


def greet() -> str:
    class LocalGreeter(GreetingBase):
        def greet(self) -> str:
            return "py:" + super().greet()

    return EventSource.greet(LocalGreeter("local", 1))
''', true)
        Context polyglot = ctx.getBean(Context)

        expect: 'the host adapter passes the constructor arguments to the Java constructor and Java reaches the Python overrides'
        polyglot.eval('python', 'count()').asString() == 'n1:local,n2:local'
        polyglot.eval('python', 'greet()').asString() == 'describe:py:Hello local x1'

        cleanup:
        ctx?.close()
    }

    private static List<String> strings(Value list) {
        (0..<list.arraySize).collect { list.getArrayElement(it).asString() }
    }
}
