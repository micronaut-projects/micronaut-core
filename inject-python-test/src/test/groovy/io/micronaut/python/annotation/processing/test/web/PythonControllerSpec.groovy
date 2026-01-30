package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.json.JsonMapper
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton

class PythonControllerSpec extends AbstractPythonTypeElementSpec {

    void "test python controller with import alias"() {
        given:
        def context = buildContext('''
import jakarta.inject as inject
import micronaut.http.annotation as http

@inject.Singleton
class MessageService:
    def say_hello(self, name : str) -> str:
        return f"Hello {name}"

@http.Controller("/hello")
class HelloController:
    def __init__(self, messageService: MessageService):
        self.messageService = messageService

    @http.Get("/{name}")
    def say_hello(self, name : str) -> str:
        return self.messageService.say_hello(name)

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/hello/John") == "Hello John"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Get
from micronaut.http import MediaType
@S
class MessageService:
    def say_hello(self, name : str) -> str:
        return f"Hello {name}"

@Controller("/hello")
class HelloController:
    def __init__(self, messageService: MessageService):
        self.messageService = messageService

    @Get(value = "/{name}", produces = MediaType.TEXT_PLAIN)
    def say_hello(self, name : str) -> str:
        return self.messageService.say_hello(name)

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)
        def definition = context.getBeanDefinition(context.classLoader.loadClass('python.HelloController'))

        expect:
        definition.getRequiredMethod("say_hello", String).stringValue(Get, "produces").get() == 'text/plain'
        client.toBlocking().retrieve("/hello/John") == "Hello John"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller map response"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Get

@S
class MessageService:
    def say_hello(self, name : str) -> dict[str, str]:
        return {
            "message": f"Hello {name}"
        }

@Controller("/hello")
class HelloController:
    def __init__(self, messageService: MessageService):
        self.messageService = messageService

    @Get("/{name}")
    def say_hello(self, name : str) -> dict[str, str]:
        return self.messageService.say_hello(name)

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/hello/John") == "{\"message\":\"Hello John\"}"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller JSON response"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Get
from micronaut.core.annotation import Introspected
from dataclasses import dataclass

@Introspected
@dataclass
class Message:
    message: str = "Who are you?"

@S
class MessageService:
    def say_hello(self, name : str) -> Message:
        text = f"Hello {name}"
        return Message(text)

@Controller("/hello")
class HelloController:
    def __init__(self, messageService: MessageService):
        self.messageService = messageService

    @Get("/{name}")
    def say_hello(self, name : str) -> Message:
        return self.messageService.say_hello(name)

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/hello/John") == "{\"message\":\"Hello John\"}"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller generic JSON response"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Get
from micronaut.core.annotation import Introspected
from micronaut.http import HttpResponse
from dataclasses import dataclass

@Introspected
@dataclass
class Message:
    message: str = "Who are you?"

@S
class MessageService:
    def say_hello(self, name : str) -> Message:
        text = f"Hello {name}"
        return Message(text)

@Controller("/hello")
class HelloController:
    def __init__(self, messageService: MessageService):
        self.messageService = messageService

    @Get("/{name}")
    def say_hello(self, name : str) -> HttpResponse[Message]:
        response = HttpResponse.ok(self.messageService.say_hello(name))
        response.header("Foo", "Bar")
        return response


''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)


        def response = client.toBlocking().exchange("/hello/John", String)
        expect:
        response.header("Foo") == "Bar"
        response.body() == "{\"message\":\"Hello John\"}"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller generic JSON exchange"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Post, Body
from micronaut.core.annotation import Introspected
from micronaut.http import HttpResponse, HttpRequest
from dataclasses import dataclass
from typing import Annotated

@Introspected
@dataclass
class Message:
    message: str = "Who are you?"

@Introspected
@dataclass
class Person:
    name: str = None

@S
class MessageService:
    def say_hello(self, name : str) -> Message:
        text = f"Hello {name}"
        return Message(text)

@Controller("/hello")
class HelloController:
    def __init__(self, messageService: MessageService):
        self.messageService = messageService

    @Post("/")
    def say_hello(self, request : HttpRequest[Person]) -> HttpResponse[Message]:
        response = HttpResponse.ok(self.messageService.say_hello(request.getBody().get().getName()))
        response.header("Foo", "Bar")
        return response


''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        def personClass = context.classLoader.loadClass('python.Person')
        def person = personClass.newInstance("John")
        def jsonMapper = context.getBean(JsonMapper)
        person = jsonMapper.readValue(jsonMapper.writeValueAsString(person), personClass)

        def response = client.toBlocking().exchange(HttpRequest.POST("/hello", person), String)
        expect:
        response.header("Foo") == "Bar"
        response.body() == "{\"message\":\"Hello John\"}"

        cleanup:
        client.close()
        context?.close()
    }

}
