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

import io.micronaut.http.client.HttpClient
import io.micronaut.json.JsonMapper
import io.micronaut.python.annotation.processing.test.classargs.TemporalApi
import io.micronaut.python.annotation.processing.test.classargs.VectorLike
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.websocket.WebSocketClient
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value

/**
 * Python classes passed to Java where a {@code Class} is expected, generated Java wrappers
 * returned to Python by Java calls, java.time values in Python and primitive varargs overloads.
 */
class PythonClassArgumentSpec extends AbstractPythonTypeElementSpec {

    void "Python classes are accepted where Java expects a Class"() {
        given:
        def context = buildContext('''
from abc import ABC, abstractmethod
from dataclasses import dataclass

import java
from jakarta.inject import Named, Singleton
from micronaut.context import BeanContext
from micronaut.core.type import Argument
from micronaut.http.annotation import Controller, Get
from micronaut.http.client.annotation import Client
from micronaut.inject.qualifiers import Qualifiers
from micronaut.python.annotation.processing.test.classargs import ClassArgumentApi
from micronaut.serde.annotation import Serdeable

String = java.type("java.lang.String")


@Serdeable
@dataclass
class Book:
    title: str
    pages: int


class Greeter(ABC):
    @abstractmethod
    def greet(self) -> str:
        ...


@Singleton
@Named("english")
class EnglishGreeter(Greeter):
    def greet(self) -> str:
        return "hello"


@Singleton
@Named("french")
class FrenchGreeter(Greeter):
    def greet(self) -> str:
        return "bonjour"


@Client("/greetings")
class GreetingClient(ABC):
    @Get("/{name}")
    @abstractmethod
    def greeting(self, name: str) -> str:
        ...


@Controller("/greetings")
class GreetingController:
    @Get("/{name}")
    def greeting(self, name: str) -> str:
        return "hi " + name


@Singleton
class Probe:
    def __init__(self, context: BeanContext):
        self.context = context

    def client_greeting(self) -> str:
        return self.context.getBean(GreetingClient).greeting("bob")

    def client_definition(self) -> str:
        return self.context.findBeanDefinition(GreetingClient).get().getBeanType().getName()

    def list_argument(self) -> str:
        argument = Argument.listOf(Book)
        return argument.getType().getName() + "<" + argument.getFirstTypeVariable().get().getType().getName() + ">"

    def of_argument(self) -> str:
        return Argument.of(Book).getType().getName()

    def of_named_argument(self) -> str:
        argument = Argument.of(Book, "book")
        return argument.getName() + ":" + argument.getType().getName()

    def of_generic_argument(self) -> str:
        argument = Argument.of(java.type("java.util.Map"), String, Book)
        return argument.getTypeString(True)

    def map_argument(self) -> str:
        return Argument.mapOf(String, Book).getTypeString(True)

    def set_argument(self) -> str:
        return Argument.setOf(Greeter).getTypeString(True)

    def greeter_count(self) -> int:
        return self.context.getBeansOfType(Greeter).size()

    def greeter_argument_count(self) -> int:
        return self.context.getBeansOfType(Argument.of(Greeter)).size()

    def french(self) -> str:
        return self.context.getBean(Greeter, Qualifiers.byName("french")).greet()

    def english(self) -> str:
        return self.context.getBean(EnglishGreeter).greet()

    def contains(self) -> bool:
        return self.context.containsBean(Greeter) and self.context.containsBean(EnglishGreeter, Qualifiers.byName("english"))

    def definition(self) -> str:
        return self.context.getBeanDefinition(EnglishGreeter).getBeanType().getName()

    def search_function(self) -> str:
        return ClassArgumentApi.search(lambda q: q + "!", Book)

    def search_query(self) -> str:
        return ClassArgumentApi.search("q", Book)

    def describe_classes(self) -> str:
        return ClassArgumentApi.describe(Book, Greeter)

    def describe_class(self) -> str:
        return ClassArgumentApi.describe(Book)

    def describe_argument(self) -> str:
        return ClassArgumentApi.describe(Argument.of(Book))

    def describe_type_arguments(self) -> str:
        return ClassArgumentApi.describe(java.type("java.util.List"), Argument.of(Book))
''', true)
        context.getBean(EmbeddedServer).start()
        def probe = getBean(context, "python.Probe").asPolyglotValue()

        expect:
        probe.invokeMember("client_greeting").asString() == "hi bob"
        probe.invokeMember("client_definition").asString() == "python.GreetingClient"
        probe.invokeMember("of_argument").asString() == "python.Book"
        probe.invokeMember("list_argument").asString() == "java.util.List<python.Book>"
        probe.invokeMember("of_named_argument").asString() == "book:python.Book"
        probe.invokeMember("of_generic_argument").asString() == "Map<String, Book>"
        probe.invokeMember("map_argument").asString() == "Map<String, Book>"
        probe.invokeMember("set_argument").asString() == "Set<Greeter>"
        probe.invokeMember("greeter_count").asInt() == 2
        probe.invokeMember("greeter_argument_count").asInt() == 2
        probe.invokeMember("french").asString() == "bonjour"
        probe.invokeMember("english").asString() == "hello"
        probe.invokeMember("contains").asBoolean()
        probe.invokeMember("definition").asString() == "python.EnglishGreeter"
        probe.invokeMember("search_function").asString() == "function:q!:python.Book"
        probe.invokeMember("search_query").asString() == "query:q:python.Book"
        probe.invokeMember("describe_classes").asString() == "python.Book,python.Greeter"
        probe.invokeMember("describe_class").asString() == "class:python.Book"
        probe.invokeMember("describe_argument").asString() == "argument:Book"
        probe.invokeMember("describe_type_arguments").asString() == "java.util.List<Book>"

        cleanup:
        context?.close()
    }

    void "a Python client WebSocket class is accepted by WebSocketClient.connect"() {
        given:
        def context = buildContext('''
from abc import ABC, abstractmethod

import java
from micronaut.websocket import WebSocketSession
from micronaut.websocket.annotation import ClientWebSocket, OnMessage, OnOpen, ServerWebSocket

AutoCloseable = java.type("java.lang.AutoCloseable")
CopyOnWriteArrayList = java.type("java.util.concurrent.CopyOnWriteArrayList")
Flux = java.type("reactor.core.publisher.Flux")
TimeUnit = java.type("java.util.concurrent.TimeUnit")


@ServerWebSocket("/echo/{name}")
class EchoServerWebSocket:
    @OnOpen
    def on_open(self, name: str, session: WebSocketSession) -> None:
        session.sendSync("welcome " + name)

    @OnMessage
    def on_message(self, name: str, message: str, session: WebSocketSession) -> None:
        session.sendSync(name + ": " + message)


@ClientWebSocket("/echo/{name}")
class EchoClientWebSocket(ABC, AutoCloseable):
    def __init__(self):
        self.replies = CopyOnWriteArrayList()

    @OnOpen
    def on_open(self, name: str, session: WebSocketSession) -> None:
        self.name = name

    @OnMessage
    def on_message(self, message: str) -> None:
        self.replies.add(message)

    def await_replies(self, count: int) -> list:
        for _ in range(50):
            if self.replies.size() >= count:
                break
            TimeUnit.MILLISECONDS.sleep(100)
        return list(self.replies)

    @abstractmethod
    def send(self, message: str) -> None:
        ...


class Exchange:
    @staticmethod
    def run(ws_client) -> list:
        client = Flux.from_(ws_client.connect(EchoClientWebSocket, "/echo/fred")).blockFirst()
        try:
            client.send("hi")
            return client.await_replies(2)
        finally:
            client.close()
''', true)
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def wsClient = context.createBean(WebSocketClient, embeddedServer.URL)
        def polyglot = context.getBean(Context)

        when:
        Value replies = polyglot.eval("python", "Exchange").invokeMember("run", wsClient)

        then:
        replies.getArraySize() == 2
        replies.getArrayElement(0).asString() == "welcome fred"
        replies.getArrayElement(1).asString() == "fred: hi"

        cleanup:
        wsClient?.close()
        context?.close()
    }

    void "a generated wrapper returned by a Java call behaves as the Python object it wraps"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass

from micronaut.http import HttpRequest
from micronaut.http.annotation import Controller, Get
from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass(frozen=True)
class Book:
    title: str
    pages: int

    def label(self) -> str:
        return self.title + " (" + str(self.pages) + ")"


@Serdeable
@dataclass
class Author:
    name: str
    books: list[Book]


@Controller("/library")
class LibraryController:
    @Get("/books/{id}")
    def book(self, id: int) -> Book:
        return Book("Micronaut", 300 + id)

    @Get("/authors/{name}")
    def author(self, name: str) -> Author:
        return Author(name, [Book("Micronaut", 301), Book("GraalPy", 200)])


class Probe:
    @staticmethod
    def read_book(mapper, json: str) -> str:
        return Probe.check_book(mapper.readValue(json, Book))

    @staticmethod
    def retrieve_book(client) -> str:
        return Probe.check_book(client.toBlocking().retrieve(HttpRequest.GET("/library/books/1"), Book))

    @staticmethod
    def read_author(mapper, json: str) -> str:
        return Probe.check_author(mapper.readValue(json, Author))

    @staticmethod
    def retrieve_author(client) -> str:
        return Probe.check_author(client.toBlocking().retrieve(HttpRequest.GET("/library/authors/Graeme"), Author))

    @staticmethod
    def check_book(book) -> str:
        expected = Book("Micronaut", 301)
        return ":".join([
            "isinstance=" + str(isinstance(book, Book)),
            "eq=" + str(book == expected),
            "req=" + str(expected == book),
            "ne=" + str(book != Book("Other", 1)),
            "label=" + book.label(),
            "title=" + book.title,
            "hash=" + str(hash(book) == hash(expected)),
            "in=" + str(book in {expected}),
            "repr=" + repr(book),
        ])

    @staticmethod
    def check_author(author) -> str:
        expected = Author("Graeme", [Book("Micronaut", 301), Book("GraalPy", 200)])
        return ":".join([
            "isinstance=" + str(isinstance(author, Author)),
            "eq=" + str(author == expected),
            "req=" + str(expected == author),
            "books=" + str(author.books == expected.books),
            "first=" + author.books[0].label(),
            "str=" + str(author),
        ])
''', true)
        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)
        def mapper = context.getBean(JsonMapper)
        def polyglot = context.getBean(Context)
        def probe = polyglot.eval("python", "Probe")
        def book = "isinstance=True:eq=True:req=True:ne=True:label=Micronaut (301):title=Micronaut:hash=True:in=True:repr=Book(title='Micronaut', pages=301)"
        def author = "isinstance=True:eq=True:req=True:books=True:first=Micronaut (301):str=Author(name='Graeme', books=[Book(title='Micronaut', pages=301), Book(title='GraalPy', pages=200)])"

        expect:
        probe.invokeMember("read_book", mapper, '{"title":"Micronaut","pages":301}').asString() == book
        probe.invokeMember("retrieve_book", client).asString() == book
        probe.invokeMember("read_author", mapper, '{"name":"Graeme","books":[{"title":"Micronaut","pages":301},{"title":"GraalPy","pages":200}]}').asString() == author
        probe.invokeMember("retrieve_author", client).asString() == author

        cleanup:
        client?.close()
        context?.close()
    }

    void "java.time values returned by Java calls are Python datetimes with their Java methods"() {
        given:
        def context = buildContext('''
from datetime import date, datetime, time, timedelta

from micronaut.python.annotation.processing.test.classargs import TemporalApi


class Probe:
    @staticmethod
    def date_time() -> str:
        value = TemporalApi.dateTime()
        return ":".join([
            str(isinstance(value, datetime)),
            str(value.year),
            value.isoformat(),
            str(value.plusDays(1).getDayOfMonth()),
            str(value == datetime(2026, 7, 21, 12, 34, 56, 123000)),
            str((value + timedelta(days=1)).day),
            TemporalApi.describe(value),
            TemporalApi.describe(value.plusDays(1)),
            TemporalApi.describe(datetime(2026, 7, 22, 1, 2, 3)),
        ])

    @staticmethod
    def date_value() -> str:
        value = TemporalApi.date()
        return ":".join([
            str(isinstance(value, date)),
            str(value.month),
            str(value.plusMonths(1).getMonthValue()),
            TemporalApi.describe(value),
        ])

    @staticmethod
    def time_value() -> str:
        value = TemporalApi.time()
        return ":".join([
            str(isinstance(value, time)),
            str(value.minute),
            str(value.plusHours(1).getHour()),
        ])

    @staticmethod
    def instant_value() -> str:
        value = TemporalApi.instant()
        return ":".join([
            str(isinstance(value, datetime)),
            str(value.getEpochSecond()),
            str(value.plusSeconds(4).getEpochSecond() - value.getEpochSecond()),
            TemporalApi.describe(value),
        ])

    @staticmethod
    def duration_value() -> str:
        value = TemporalApi.duration()
        return ":".join([
            str(value.toMinutes()),
            TemporalApi.describe(value),
            TemporalApi.describe(timedelta(minutes=5)),
        ])
''', true)
        def polyglot = context.getBean(Context)
        def probe = polyglot.eval("python", "Probe")

        expect:
        probe.invokeMember("date_time").asString() == "True:2026:2026-07-21T12:34:56.123000:22:True:22:datetime:2026-07-21T12:34:56.123:datetime:2026-07-22T12:34:56.123:datetime:2026-07-22T01:02:03"
        probe.invokeMember("date_value").asString() == "True:7:8:date:2026-07-21"
        probe.invokeMember("time_value").asString() == "True:34:13"
        probe.invokeMember("instant_value").asString() == "True:" + TemporalApi.instant().epochSecond + ":4:instant:2026-07-21T12:34:56.123Z"
        probe.invokeMember("duration_value").asString() == "90:duration:PT1H30M:duration:PT5M"

        cleanup:
        context?.close()
    }

    void "primitive varargs overloads accept Python numbers"() {
        given:
        def context = buildContext('''
from micronaut.python.annotation.processing.test.classargs import VectorLike


def describe(vector) -> str:
    return vector.kind() + ":" + ",".join(str(round(v, 6)) for v in vector.toDoubleArray())


class Probe:
    @staticmethod
    def floats() -> str:
        return describe(VectorLike.of(0.1, 0.2, 0.3))

    @staticmethod
    def repeated() -> str:
        return ";".join([
            describe(VectorLike.of(0.1, 0.2, 0.3)),
            describe(VectorLike.of(0.15, 0.2, 0.25)),
            describe(VectorLike.of(0.9, 0.1, 0.1)),
            describe(VectorLike.of(0.5, 0.25, 0.75)),
            describe(VectorLike.of(1, 2, 3)),
            describe(VectorLike.of(300, 1)),
            describe(VectorLike.of(1, 2.5)),
            describe(VectorLike.of(0.1, 0.2, 0.3)),
        ])
''', true)
        def polyglot = context.getBean(Context)
        def probe = polyglot.eval("python", "Probe")

        expect:
        VectorLike.assertionsEnabled()
        probe.invokeMember("floats").asString() == "double:0.1,0.2,0.3"
        probe.invokeMember("repeated").asString() == "double:0.1,0.2,0.3;double:0.15,0.2,0.25;double:0.9,0.1,0.1;float:0.5,0.25,0.75;byte:1.0,2.0,3.0;float:300.0,1.0;float:1.0,2.5;double:0.1,0.2,0.3"

        cleanup:
        context?.close()
    }
}
