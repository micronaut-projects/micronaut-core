package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer

class PythonControllerSpec extends AbstractPythonTypeElementSpec {

    void "test python controller"() {
        given:
        def context = buildContext('''
from jakarta.inject import Singleton
from io.micronaut.http.annotation import Controller, Get

@Singleton
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
}
