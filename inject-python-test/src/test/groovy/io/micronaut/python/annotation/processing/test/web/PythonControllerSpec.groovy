package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.http.client.HttpClient
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton

class PythonControllerSpec extends AbstractPythonTypeElementSpec {

    void "test python controller"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton as S
from micronaut.http.annotation import Controller, Get

@S
class MessageService:
    def say_hello(self, name : str) -> str:
        return f"Hello {name}"

@Controller("/hello")
class HelloController:
    def __init__(self, messageService: MessageService):
        self.messageService = messageService

    @Get("/{name}")
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

}
