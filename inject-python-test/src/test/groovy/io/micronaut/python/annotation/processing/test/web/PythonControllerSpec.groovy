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

    void "test classless python controller route with execute on"() {
        given:
        def context = buildContext('''
from micronaut.http.annotation import Get
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn

@ExecuteOn(TaskExecutors.BLOCKING)
@Get(value="/classless/{name}", produces="text/plain")
def say_hello(name : str) -> str:
    return f"Hello {name}"

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/classless/John") == "Hello John"

        cleanup:
        client.close()
        context?.close()
    }

    void "test classless python controller generic JSON response"() {
        given:
        def context = buildContext('''
from micronaut.http.annotation import Get
from micronaut.core.annotation import Introspected
from micronaut.http import HttpResponse
from dataclasses import dataclass

@Introspected
@dataclass
class Message:
    message: str = "Who are you?"

@Get("/classless/generic/{name}")
def say_hello(name : str) -> HttpResponse[Message]:
    response = HttpResponse.ok(Message(f"Hello {name}"))
    response.header("Foo", "Bar")
    return response

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        def response = client.toBlocking().exchange("/classless/generic/John", String)
        expect:
        response.header("Foo") == "Bar"
        response.body() == "{\"message\":\"Hello John\"}"

        cleanup:
        client.close()
        context?.close()
    }

    void "test classless python controller generic JSON entity body response"() {
        given:
        def context = buildContext('''
from typing import Annotated
from micronaut.http.annotation import Body, Post
from micronaut.core.annotation import Introspected
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.http import HttpResponse
from dataclasses import dataclass

@Introspected
@dataclass
@MappedEntity
class Genre:
    id: Annotated[int | None, Id, GeneratedValue] = None
    name: str | None = None

@Post("/classless/generic/entity")
def save(genre : Annotated[Genre, Body]) -> HttpResponse[Genre]:
    response = HttpResponse.created(genre)
    response.header("Foo", "Bar")
    return response

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)
        def controllerClass = context.classLoader.loadClass("python.Script")
        def genreClass = context.classLoader.loadClass("python.Genre")
        def saveMethod = controllerClass.getDeclaredMethod("save", genreClass)

        def response = client.toBlocking().exchange(HttpRequest.POST("/classless/generic/entity", [name: "DevOps"]), String)
        expect:
        saveMethod.genericReturnType.typeName == "io.micronaut.http.HttpResponse<python.Genre>"
        response.header("Foo") == "Bar"
        response.body() == "{\"name\":\"DevOps\"}"

        cleanup:
        client.close()
        context?.close()
    }

    void "test classless python controller generic JSON entity response from Java value"() {
        given:
        def context = buildContext('''
import java
from typing import Annotated
from micronaut.http.annotation import Body, Post
from micronaut.core.annotation import Introspected
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.http import HttpResponse
from dataclasses import dataclass

JavaStore = java.type("io.micronaut.python.annotation.processing.test.web.PythonControllerSpec$JavaStore")

@Introspected
@dataclass
@MappedEntity
class Genre:
    id: Annotated[int | None, Id, GeneratedValue] = None
    name: str | None = None

@Post("/classless/generic/java-entity")
def save(genre : Annotated[Genre, Body]) -> HttpResponse[Genre]:
    saved = JavaStore.save(genre)
    response = HttpResponse.created(saved)
    response.header("Foo", "Bar")
    return response

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)
        def controllerClass = context.classLoader.loadClass("python.Script")
        def genreClass = context.classLoader.loadClass("python.Genre")
        def saveMethod = controllerClass.getDeclaredMethod("save", genreClass)

        def response = client.toBlocking().exchange(HttpRequest.POST("/classless/generic/java-entity", [name: "DevOps"]), String)
        expect:
        saveMethod.genericReturnType.typeName == "io.micronaut.http.HttpResponse<python.Genre>"
        response.header("Foo") == "Bar"
        response.body() == "{\"name\":\"DevOps\"}"

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

    void "test python controller serializes nested serdeable list properties"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from jakarta.inject import Singleton
from micronaut.http.annotation import Controller, Get
from micronaut.python.compiler import Serdeable


@Serdeable
@dataclass
class Period:
    temperature: int = 0


@Serdeable
@dataclass
class ForecastProperties:
    periods: list[Period] | None = None


@Serdeable
@dataclass
class Forecast:
    properties: ForecastProperties | None = None


@Singleton
class ForecastService:
    def forecast(self) -> Forecast:
        return Forecast(ForecastProperties(periods=[Period(68)]))


@Controller("/forecast")
class ForecastController:
    def __init__(self, forecastService: ForecastService):
        self.forecastService = forecastService

    @Get("/")
    def forecast(self) -> Forecast:
        return self.forecastService.forecast()

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/forecast") == '{"properties":{"periods":[{"temperature":68}]}}'

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

        def json = jsonMapper.writeValueAsString(person)
        person = jsonMapper.readValue(json, personClass)

        def response = client.toBlocking().exchange(HttpRequest.POST("/hello", person), String)
        expect:
        response.header("Foo") == "Bar"
        response.body() == "{\"message\":\"Hello John\"}"

        cleanup:
        client.close()
        context?.close()
    }

    static final class JavaStore {
        static Object save(Object value) {
            if (!value.getClass().getName().equals('python.Genre')) {
                throw new IllegalArgumentException("Expected generated python.Genre wrapper but got " + value.getClass().getName())
            }
            return value
        }
    }

}
