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
package io.micronaut.python.annotation.processing.test.web

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer

/**
 * A {@code @Serdeable @dataclass} request body is a dataclass instance in the route and in the
 * services the route passes it to: {@code dataclasses.is_dataclass} and {@code asdict} work on it
 * and a missing optional field is {@code None}.
 */
class DataclassRequestBodySpec extends AbstractPythonTypeElementSpec {

    void "test a dataclass request body is a dataclass instance in the route and in a service"() {
        given:
        def context = buildContext('''
import dataclasses
import enum
from dataclasses import dataclass, field
from typing import Annotated

from jakarta.inject import Singleton
from micronaut.http.annotation import Body, Controller, Post
from micronaut.serde.annotation import Serdeable


class ReadingStatus(enum.Enum):
    WANT_TO_READ = "WANT_TO_READ"
    READING = "READING"


@Serdeable
@dataclass
class BookRequest:
    name: str
    count: int
    note: str | None = None
    status: ReadingStatus = ReadingStatus.WANT_TO_READ
    tags: list[str] = field(default_factory=list)


def describe(where: str, body) -> str:
    data = dataclasses.asdict(body)
    return "|".join([
        where,
        type(body).__name__,
        str(dataclasses.is_dataclass(body)),
        str(isinstance(body, BookRequest)),
        type(body.note).__name__,
        str(body.note is None),
        str(data["note"] is None),
        data["name"],
        str(data["count"]),
        data["status"].name,
        ",".join(data["tags"]),
        str(dataclasses.replace(body, count=9).count),
    ])


@Singleton
class BookService:
    def describe(self, body: BookRequest) -> str:
        return describe("service", body)


@Controller("/dataclass-body")
class BookController:
    def __init__(self, service: BookService):
        self.service = service

    @Post("/route")
    def route(self, body: Annotated[BookRequest, Body]) -> str:
        return describe("route", body)

    @Post("/service")
    def service_call(self, body: Annotated[BookRequest, Body]) -> str:
        return self.service.describe(body)
''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL).toBlocking()

        expect:
        client.retrieve(HttpRequest.POST("/dataclass-body/route", '{"name":"x","count":1,"tags":["a","b"]}')) ==
            "route|BookRequest|True|True|NoneType|True|True|x|1|WANT_TO_READ|a,b|9"
        client.retrieve(HttpRequest.POST("/dataclass-body/service", '{"name":"y","count":2}')) ==
            "service|BookRequest|True|True|NoneType|True|True|y|2|WANT_TO_READ||9"

        cleanup:
        embeddedServer?.stop()
        context?.close()
    }

    void "test the attributes a Python method assigns on a dataclass argument reach the Java getters"() {
        given:
        def context = buildContext('''
import dataclasses
from dataclasses import dataclass, field

from jakarta.inject import Singleton
from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class Order:
    name: str
    count: int
    note: str | None = None
    tags: list[str] = field(default_factory=list)


@Singleton
class OrderService:
    def update(self, order: Order) -> str:
        before = f"{type(order).__name__}|{dataclasses.is_dataclass(order)}|{type(order.note).__name__}|{order.count}"
        order.count += 1
        order.note = None if order.note else "seen"
        order.tags.append("updated")
        return before
''')
        def orderClass = context.classLoader.loadClass("python.Order")
        def order = orderClass.constructors.find { it.parameterCount == 4 }.newInstance("x", 1, null, ["a"])
        def service = context.getBean(context.classLoader.loadClass("python.OrderService"))

        expect: "the method receives the dataclass and its changes are visible through the Java getters"
        service.update(order) == "Order|True|NoneType|1"
        order.count == 2
        order.note == "seen"
        order.tags == ["a", "updated"]

        and: "a second call sees the state the first one left, and an attribute set to None is null in Java"
        service.update(order) == "Order|True|str|2"
        order.count == 3
        order.note == null
        order.tags == ["a", "updated", "updated"]

        cleanup:
        context?.close()
    }
}
