package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer

class PythonControllerSpec extends AbstractPythonTypeElementSpec {

    void "test python controller"() {
        given:
        def context = buildContext('''
from io.micronaut.http.annotation import Controller, Get

@Controller("/hello")
class HelloController:
    @Get("/{name}")
    def say_hello(self, name : str) -> str:
        return f"Hello {name}"
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
