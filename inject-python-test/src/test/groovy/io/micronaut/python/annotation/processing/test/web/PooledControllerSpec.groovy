package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class PooledControllerSpec extends AbstractPythonTypeElementSpec {

    void "pooled controller uses multiple contexts and injection works"() {
        given:
        def python = '''
from micronaut.http.annotation import Get
from typing import Annotated
from jakarta.inject import Inject, Singleton

@Singleton
class MessageService:
    def say_hello(self, name : str) -> str:
        return f"Hello {name}"

message_service : Annotated[MessageService, Inject]

@Get("/pool/ctx")
def ctx() -> str:
    import builtins
    g = globals()
    return g.get("__MN_CTX_ID__") or builtins.__dict__.get("__MN_CTX_ID__") or "unknown"

@Get("/pool/hello/{name}")
def hello(name: str) -> str:
    return message_service.say_hello(name)
'''
        Map<String, Object> props = ["micronaut.python.pool.size": 4, "micronaut.python.pool.sync-init":true]
        ApplicationContext context = buildContext(python, true, props)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)

        when:
        def ids = (1..12).collect { client.toBlocking().retrieve("/pool/ctx") }.toSet()

        then:
        ids.size() >= 2

        when:
        def msg = client.toBlocking().retrieve("/pool/hello/John")

        then:
        msg == "Hello John"

        cleanup:
        client?.close()
        context?.close()
    }
}
