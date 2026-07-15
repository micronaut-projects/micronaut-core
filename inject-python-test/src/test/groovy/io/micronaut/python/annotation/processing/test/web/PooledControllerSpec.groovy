package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PooledControllerSpec extends AbstractPythonTypeElementSpec {

    void "pooled controller uses multiple contexts and injection works"() {
        given:
        def python = '''
from micronaut.http.annotation import Get
from typing import Annotated
from jakarta.inject import Inject, Singleton
from micronaut.context.python.scope import ContextPooled

@ContextPooled
@Singleton
class MessageService:
    def say_hello(self, name : str) -> str:
        return f"Hello {name}"
    def ctx_id(self) -> str:
        import builtins
        g = globals()
        return g.get("__MN_CTX_ID__") or builtins.__dict__.get("__MN_CTX_ID__") or "unknown"

message_service : Annotated[MessageService, Inject]

@Get("/pool/ctx")
def ctx() -> str:
    import time
    time.sleep(0.1)
    import builtins
    g = globals()
    return g.get("__MN_CTX_ID__") or builtins.__dict__.get("__MN_CTX_ID__") or "unknown"

@Get("/pool/hello/{name}")
def hello(name: str) -> str:
    return message_service.say_hello(name)

@Get("/pool/pair")
def pair() -> str:
    own = ctx()
    return own + ":" + message_service.ctx_id()
'''
        def previousContextIdProperty = System.getProperty("micronaut.python.context-id.enabled")
        System.setProperty("micronaut.python.context-id.enabled", "true")
        Map<String, Object> props = ["micronaut.python.pool.size": 4]
        ApplicationContext context = buildContext(python, true, props)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)
        def executor = Executors.newFixedThreadPool(4)

        when:
        def ids = (1..4).collect {
            executor.submit { client.toBlocking().retrieve("/pool/ctx") }
        }.collect {
            it.get(5, TimeUnit.SECONDS)
        }.toSet()

        then:
        ids.size() >= 2

        when:
        def msg = client.toBlocking().retrieve("/pool/hello/John")

        then:
        msg == "Hello John"

        when:
        def pairs = (1..12).collect { client.toBlocking().retrieve("/pool/pair") }

        then:
        pairs.every { pair ->
            def parts = pair.split(":")
            parts.length == 2 && parts[0] == parts[1]
        }
        pairs.toSet().size() >= 2

        cleanup:
        executor?.shutdownNow()
        client?.close()
        context?.close()
        if (previousContextIdProperty == null) {
            System.clearProperty("micronaut.python.context-id.enabled")
        } else {
            System.setProperty("micronaut.python.context-id.enabled", previousContextIdProperty)
        }
    }
}
