from typing import Annotated
from uuid import uuid4

import java
from jakarta.inject import Inject, Singleton
from micronaut.context import ApplicationContext
from micronaut.context.annotation import Property, Requires
from micronaut.http import HttpRequest
from micronaut.http.annotation import Controller, Get
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.runtime.http.scope import RequestAware, RequestScope
from micronaut.runtime.context.scope import Refreshable
from micronaut.runtime.context.scope.refresh import RefreshEvent
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test


@Requires(property="spec.name", value="PythonScopedProxySpec")
@Refreshable
class ScopedIdentifier:
    def __init__(self):
        self._value = str(uuid4())

    @property
    def value(self) -> str:
        return self._value

    def current(self) -> str:
        return self._value


@Requires(property="spec.name", value="PythonScopedProxySpec")
@Singleton
class ScopedIdentifierHolder:
    def __init__(self, identifier: ScopedIdentifier):
        self.identifier = identifier

    def current_property(self) -> str:
        return self.identifier.value

    def current_method(self) -> str:
        return self.identifier.current()


@Requires(property="spec.name", value="PythonScopedProxySpec")
@RequestScope
class RequestIdentifier(RequestAware):
    def __init__(self):
        self._value = ""

    @property
    def value(self) -> str:
        return self._value

    def current(self) -> str:
        return self._value

    def setRequest(self, request: HttpRequest):
        self._value = request.getHeaders().get("X-Request-Id")


@Requires(property="spec.name", value="PythonScopedProxySpec")
@Singleton
class RequestIdentifierHolder:
    def __init__(self, identifier: RequestIdentifier):
        self.identifier = identifier

    def current_property(self) -> str:
        return self.identifier.value

    def current_method(self) -> str:
        return self.identifier.current()


@Requires(property="spec.name", value="PythonScopedProxySpec")
@Controller("/scoped-proxy")
class RequestIdentifierController:
    def __init__(self, holder: RequestIdentifierHolder):
        self.holder = holder

    @Get("/identifier")
    def identifier(self) -> str:
        value = self.holder.current_property()
        assert value == self.holder.current_method()
        return value


@Property(name="spec.name", value="PythonScopedProxySpec")
@MicronautTest
class PythonScopedProxySpec:
    context: Annotated[ApplicationContext, Inject] = None
    client: Annotated[HttpClient, Inject, Client("/")] = None

    @Test
    def test_refreshable_direct_injection_uses_current_scoped_target(self):
        holder_type = java.type("micronaut.docs.inject.scope.ScopedIdentifierHolder")
        holder = self.context.getBean(holder_type).asPolyglotValue()

        first_property = holder.current_property()
        first_method = holder.current_method()
        assert first_property == first_method

        self.context.publishEvent(RefreshEvent())

        second_property = holder.current_property()
        second_method = holder.current_method()
        assert second_property == second_method
        assert first_property != second_property

    @Test
    def test_request_scope_direct_injection_uses_current_scoped_target(self):
        first = self.client.toBlocking().retrieve(
            HttpRequest.GET("/scoped-proxy/identifier").header("X-Request-Id", "first")
        )
        second = self.client.toBlocking().retrieve(
            HttpRequest.GET("/scoped-proxy/identifier").header("X-Request-Id", "second")
        )

        assert first == "first"
        assert second == "second"
