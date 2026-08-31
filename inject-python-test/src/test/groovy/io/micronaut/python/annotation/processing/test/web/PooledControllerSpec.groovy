package io.micronaut.python.annotation.processing.test.web

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PooledControllerSpec extends AbstractPythonTypeElementSpec {

    void "pooled controller uses multiple contexts and injection works"() {
        given:
        def python = '''
from dataclasses import dataclass
from enum import Enum
from micronaut.core.annotation import Introspected
from micronaut.http.annotation import Body, Get, Post
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

def context_id() -> str:
    import builtins
    g = globals()
    return g.get("__MN_CTX_ID__") or builtins.__dict__.get("__MN_CTX_ID__") or "unknown"

@Introspected
@dataclass(frozen=True)
class Address:
    city: str

class Priority(Enum):
    HIGH = "high"
    LOW = "low"

@Introspected
@dataclass
class Order:
    customer: str
    address: Address
    items: list[str]
    metadata: dict[str, str]
    priority: Priority

    def ctx_id(self) -> str:
        return context_id()

@Introspected
class InventoryItem:
    sku: str
    quantity: int
    tags: list[str]
    metadata: dict[str, str]

    def ctx_id(self) -> str:
        return context_id()

@Get("/pool/ctx")
def ctx() -> str:
    import time
    time.sleep(0.1)
    return context_id()

@Get("/pool/hello/{name}")
def hello(name: str) -> str:
    return message_service.say_hello(name)

@Get("/pool/pair")
def pair() -> str:
    own = ctx()
    return own + ":" + message_service.ctx_id()

@Post("/pool/body")
def body(order: Annotated[Order, Body]) -> str:
    return f"{order.customer}:{order.address.city}:{','.join(order.items)}:{order.metadata['source']}:{order.priority.value}:{order.ctx_id()}:{context_id()}"

@Post("/pool/plain-body")
def plain_body(item: Annotated[InventoryItem, Body]) -> str:
    return f"{item.sku}:{item.quantity}:{','.join(item.tags)}:{item.metadata['source']}:{item.ctx_id()}:{context_id()}"
'''
        def previousContextIdProperty = System.getProperty("micronaut.python.context-id.enabled")
        System.setProperty("micronaut.python.context-id.enabled", "true")
        Map<String, Object> props = ["micronaut.python.pool.size": 4]
        ApplicationContext context = buildContext(python, true, props)
        def server = context.getBean(EmbeddedServer)
        server.start()
        def client = context.createBean(HttpClient, server.URL)
        def executor = Executors.newFixedThreadPool(4)
        warmPool(context, executor, 4)

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

        when:
        def bodyResponses = (1..8).collect {
            executor.submit {
                def request = HttpRequest.POST("/pool/body", [
                    customer: "Ada",
                    address: [city: "Zürich"],
                    items: ["compiler", "runtime"],
                    metadata: [source: "json"],
                    priority: "HIGH"
                ]).contentType(MediaType.APPLICATION_JSON_TYPE)
                client.toBlocking().retrieve(request)
            }
        }.collect {
            it.get(10, TimeUnit.SECONDS)
        }

        then:
        bodyResponses.every {
            def parts = it.split(":")
            parts.length == 7 &&
                parts[0] == "Ada" &&
                parts[1] == "Zürich" &&
                parts[2] == "compiler,runtime" &&
                parts[3] == "json" &&
                parts[4] == "high" &&
                parts[5] == parts[6]
        }
        bodyResponses.collect { it.split(":")[6] }.toSet().size() >= 2

        when:
        def plainBodyResponses = (1..8).collect {
            executor.submit {
                def request = HttpRequest.POST("/pool/plain-body", [
                    sku: "MN-PY",
                    quantity: 4,
                    tags: ["plain", "introspected"],
                    metadata: [source: "json"]
                ]).contentType(MediaType.APPLICATION_JSON_TYPE)
                client.toBlocking().retrieve(request)
            }
        }.collect {
            it.get(10, TimeUnit.SECONDS)
        }

        then:
        plainBodyResponses.every {
            def parts = it.split(":")
            parts.length == 6 &&
                parts[0] == "MN-PY" &&
                parts[1] == "4" &&
                parts[2] == "plain,introspected" &&
                parts[3] == "json" &&
                parts[4] == parts[5]
        }
        plainBodyResponses.collect { it.split(":")[5] }.toSet().size() >= 2

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
