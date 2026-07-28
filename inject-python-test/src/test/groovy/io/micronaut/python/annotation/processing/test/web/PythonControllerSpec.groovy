package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.multipart.MultipartBody
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

    void "test python controller binds multipart part parameter"() {
        given:
        def context = buildContext('''
from typing import Annotated
from micronaut.http import MediaType
from micronaut.http.annotation import Controller, Part, Post

@Controller("/form")
class FormController:
    @Post(value = "/name", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    def name(self, name: Annotated[str, Part]) -> str:
        return name

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)
        def body = MultipartBody.builder().addPart("name", "sherlock").build()

        expect:
        client.toBlocking().retrieve(
            HttpRequest.POST("/form/name", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        ) == "sherlock"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller can return reactive http response"() {
        given:
        def context = buildContext('''
import java
from typing import Annotated
from jakarta.inject import Singleton
from micronaut.core.async_.annotation import SingleResult
from micronaut.http import HttpResponse, HttpStatus, MediaType, MutableHttpResponse
from micronaut.http.annotation import Controller, Part, Post

Flux = java.type("reactor.core.publisher.Flux")
Publisher = java.type("org.reactivestreams.Publisher")

@Singleton
class RedirectSource:
    def responses(self) -> Publisher:
        return Flux.just("ok")

@Controller("/reactive-login")
class ReactiveController:
    def __init__(self, source: RedirectSource):
        self.source = source

    @SingleResult
    @Post(
        consumes=[MediaType.TEXT_HTML, MediaType.MULTIPART_FORM_DATA],
        produces=MediaType.TEXT_HTML,
    )
    def redirect(self, name: Annotated[str | None, Part] = None) -> Publisher[MutableHttpResponse]:
        return (
            Flux.from_(self.source.responses())
            .map(lambda ignored: HttpResponse.status(HttpStatus.SEE_OTHER).header("Location", f"/target/{name}"))
            .defaultIfEmpty(HttpResponse.status(HttpStatus.UNAUTHORIZED))
        )

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(
            HttpClient,
            embeddedServer.URL,
            new DefaultHttpClientConfiguration(followRedirects: false)
        )
        def body = MultipartBody.builder().addPart("name", "sherlock").build()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.POST("/reactive-login", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )

        then:
        response.status().code == 303
        response.header("Location") == "/target/sherlock"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller can return unannotated http response host object"() {
        given:
        def context = buildContext('''
from micronaut.http import HttpResponse, HttpStatus, MediaType
from micronaut.http.annotation import Controller, Post

@Controller("/untyped-response")
class UntypedResponseController:
    @Post(consumes=MediaType.APPLICATION_FORM_URLENCODED, produces=MediaType.TEXT_HTML)
    def redirect(self):
        return HttpResponse.status(HttpStatus.SEE_OTHER).header("Location", "/target")

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(
            HttpClient,
            embeddedServer.URL,
            new DefaultHttpClientConfiguration(followRedirects: false)
        )

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.POST("/untyped-response", "").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE),
            String
        )

        then:
        response.status().code == 303
        response.header("Location") == "/target"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller replaces generic java controller with reactive http response"() {
        given:
        def context = buildContext('''
import java
from typing import Annotated
from micronaut.context.annotation import Replaces
from micronaut.core.async_.annotation import SingleResult
from micronaut.http import HttpResponse, HttpStatus, MediaType, MutableHttpResponse
from micronaut.http.annotation import Controller, Part, Post
from io.micronaut.python.annotation.processing.test.web import GenericLoginController

Flux = java.type("reactor.core.publisher.Flux")
Publisher = java.type("org.reactivestreams.Publisher")

@Replaces(GenericLoginController)
@Controller("/generic-login")
class ReplacementLoginController:
    @SingleResult
    @Post(
        consumes=[MediaType.TEXT_HTML, MediaType.MULTIPART_FORM_DATA],
        produces=MediaType.TEXT_HTML,
    )
    def login(self, name: Annotated[str | None, Part] = None) -> Publisher[MutableHttpResponse]:
        return (
            Flux.just(name)
            .map(lambda n: HttpResponse.status(HttpStatus.SEE_OTHER).header("Location", f"/target/{n}"))
            .defaultIfEmpty(HttpResponse.status(HttpStatus.UNAUTHORIZED))
        )

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(
            HttpClient,
            embeddedServer.URL,
            new DefaultHttpClientConfiguration(followRedirects: false)
        )
        def replacedControllerType = context.classLoader.loadClass("io.micronaut.python.annotation.processing.test.web.GenericLoginController")
        def replacementControllerType = context.classLoader.loadClass("python.ReplacementLoginController")
        def body = MultipartBody.builder().addPart("name", "sherlock").build()

        when:
        def response = client.toBlocking().exchange(
            HttpRequest.POST("/generic-login", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String
        )

        then:
        context.getBeanDefinitions(replacedControllerType).isEmpty()
        context.getBeanDefinitions(replacementControllerType).size() == 1
        response.status().code == 303
        response.header("Location") == "/target/sherlock"

        cleanup:
        client.close()
        context?.close()
    }

    void "test python controller binds path variable to Python enum value"() {
        given:
        def context = buildContext('''
from enum import Enum
from micronaut.http.annotation import Controller, Get


class Player(Enum):
    WHITE = "w"
    BLACK = "b"


@Controller("/game")
class GameController:
    @Get("/{player}")
    def player(self, player: Player) -> str:
        return player.value

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/game/w") == "w"
        client.toBlocking().retrieve("/game/b") == "b"

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

    void "test classless python controller route with media type constant"() {
        given:
        def context = buildContext('''
from micronaut.http import MediaType
from micronaut.http.annotation import Get

@Get(value="/classless/media", produces=MediaType.TEXT_PLAIN)
def media() -> str:
    return "media"

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/classless/media") == "media"

        cleanup:
        client.close()
        context?.close()
    }

    void "test classless python controller with localized message source"() {
        given:
        def context = buildContext('''
from typing import Annotated

from jakarta.inject import Inject, Singleton
from java.util import Locale
from micronaut.context import LocalizedMessageSource, MessageSource, StaticMessageSource
from micronaut.context.annotation import Factory
from micronaut.http import MediaType
from micronaut.http.annotation import Get

message_source: Annotated[LocalizedMessageSource, Inject]

@Factory
class MessageSourceFactory:
    @Singleton
    def create_message_source(self) -> MessageSource:
        source = StaticMessageSource()
        source.addMessage("hello.world", "Hello World")
        source.addMessage(Locale("es"), "hello.world", "Hola Mundo")
        return source

@Get(value="/classless/i18n", produces=MediaType.TEXT_PLAIN)
def message() -> str:
    return message_source.getMessage("hello.world").orElse("missing")

''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve(HttpRequest.GET("/classless/i18n").header(HttpHeaders.ACCEPT_LANGUAGE, "es")) == "Hola Mundo"
        client.toBlocking().retrieve("/classless/i18n") == "Hello World"

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

    void "test classless raw http response converts model body wrappers"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field
from micronaut.core.annotation import Introspected
from micronaut.http.annotation import Get
from micronaut.http import HttpResponse

@Introspected
@dataclass
class Message:
    text: str

@Introspected
@dataclass
class Room:
    name: str
    messages: list[Message] = field(default_factory=list)

@Get("/classless/model")
def model() -> HttpResponse:
    return HttpResponse.ok({"room": Room("lobby", [Message("hello")])})

''', true)

        def controllerClass = context.classLoader.loadClass("python.Script")
        def roomClass = context.classLoader.loadClass("python.Room")
        def messageClass = context.classLoader.loadClass("python.Message")
        def controller = context.getBean(controllerClass)
        def response = controllerClass.getDeclaredMethod("model").invoke(controller)
        def body = response.getBody().get()
        def room = body["room"]

        expect:
        body instanceof Map
        roomClass.isInstance(room)
        room.getName() == "lobby"
        room.getMessages().size() == 1
        messageClass.isInstance(room.getMessages().get(0))
        room.getMessages().get(0).getText() == "hello"

        cleanup:
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

    void "test python controller returns dataclass from validated service"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import Annotated

from jakarta.inject import Singleton
from jakarta.validation.constraints import Pattern
from micronaut.core.annotation import Introspected
from micronaut.http.annotation import Controller, Get
from micronaut.validation import Validated


@Introspected
@dataclass(frozen=True)
class Greeting:
    id: int
    content: str


@Singleton
@Validated
class GreetingService:
    def __init__(self):
        self.counter = 0

    def greeting(self, name: Annotated[str, Pattern(regexp="\\\\D+")]) -> Greeting:
        self.counter += 1
        return Greeting(self.counter, f"Hola, {name}!")


@Controller("/validated-greetings")
class GreetingController:
    def __init__(self, greetingService: GreetingService):
        self.greetingService = greetingService

    @Get("/{name}")
    def greeting(self, name: str) -> Greeting:
        return self.greetingService.greeting(name)
''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        context.getBean(JsonMapper).readValue(client.toBlocking().retrieve("/validated-greetings/John"), Map) == [
            content: "Hola, John!",
            id: 1
        ]

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

    void "test inherited controller methods"() {
        given:
        def context = buildContext('''
from micronaut.http.annotation import Controller, Get

class BaseController:
    @Get("/base/{name}")
    def base(self, name: str) -> str:
        return f"Base {name}"

class ParentController(BaseController):
    @Get("/parent/{name}")
    def parent(self, name: str) -> str:
        return f"Parent {name}"

@Controller("/inherited")
class InheritedController(ParentController):
    @Get("/child/{name}")
    def child(self, name: str) -> str:
        return f"Child {name}"
''', true)

        def embeddedServer = context.getBean(EmbeddedServer)
        embeddedServer.start()
        def client = context.createBean(HttpClient, embeddedServer.URL)

        expect:
        client.toBlocking().retrieve("/inherited/base/John") == "Base John"
        client.toBlocking().retrieve("/inherited/parent/John") == "Parent John"
        client.toBlocking().retrieve("/inherited/child/John") == "Child John"

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
