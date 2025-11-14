package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.PendingFeature

class FieldInjectionSpec extends AbstractPythonTypeElementSpec {
    void "test field injection with Annotated[Type, Inject] syntax - imported type"() {
        given: "Python code with field injection"
        def pythonCode = '''
from typing import Annotated
from jakarta.inject import Singleton, Inject
from micronaut.context.annotation import Executable
from micronaut.http.client import HttpClient
from micronaut.runtime.server import EmbeddedServer
from micronaut.http.client.annotation import Client
from jakarta.validation.constraints import NotNull
from micronaut.context.annotation import Executable
from typing import Annotated

@Singleton
class MainService:
    server : Annotated[EmbeddedServer, Inject] = None
    client: Annotated[HttpClient, Inject, Client("/")] = None

    @Executable
    def get_message(self) -> str:
        if self.client is not None and self.server is not None:
            return "has client and server"
        else:
            return "has not client"
'''

        when: "Building ApplicationContext and getting the bean definition"
        def context = buildContext(pythonCode, true) // Don't include all beans to avoid missing dependencies
        def definition = getBeanDefinition(context, "python.MainService")
        context.getBean(EmbeddedServer).start()

        then: "Field setter method parameter types should be resolved correctly"
        // Field injection generates setter methods, so check injectedMethods
        def serverSetter = definition.injectedMethods.find { it.methodName == 'server' }
        def clientSetter = definition.injectedMethods.find { it.methodName == 'client' }

        serverSetter != null
        // Check that the setter method parameter has correct type
        def serverParam = serverSetter.arguments[0]
        serverParam.type.name == 'io.micronaut.runtime.server.EmbeddedServer'
        clientSetter.arguments[0].type == HttpClient
        clientSetter.arguments[0].getAnnotationMetadata().stringValue(Client).get() == '/'
        when:
        def bean = getBean(context, "python.MainService")

        then:
        bean.get_message() == "has client and server"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test field injection with Annotated[Type, Inject] syntax"() {
        given: "Python code with field injection"
        def pythonCode = '''
from typing import Annotated
from jakarta.inject import Singleton, Inject
from micronaut.context.annotation import Executable

@Singleton
class HelperService:
    @Executable
    def get_help(self) -> str:
        return "I am helping!"

@Singleton
class MainService:
    helper: Annotated[HelperService, Inject] = None

    @Executable
    def get_combined_message(self) -> str:
        if self.helper is None:
            return "No helper available"
        return "Main service with: " + self.helper.get_help()
'''

        when: "Building ApplicationContext and getting the main service"
        def context = buildContext(pythonCode)
        def mainService = getBean(context, "python.MainService")

        then: "Field injection should work"
        mainService.get_combined_message() == "Main service with: I am helping!"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    @PendingFeature(reason = "nullable not yet supported on attributes")
    void "test field injection with Annotated[Type, Inject] syntax - nullable"() {
        given: "Python code with field injection"
        def pythonCode = '''
from typing import Annotated
from jakarta.inject import Singleton, Inject
from micronaut.context.annotation import Executable

class HelperService:
    def get_help(self) -> str:
        return "I am helping!"

@Singleton
class MainService:
    helper: Annotated[HelperService | None, Inject] = None

    @Executable
    def get_combined_message(self) -> str:
        if self.helper is None:
            return "No helper available"
        return "Main service with: " + self.helper.get_help()
'''

        when: "Building ApplicationContext and getting the main service"
        def context = buildContext(pythonCode)
        def mainService = getBean(context, "python.MainService")

        then: "Field injection should work"
        mainService.get_combined_message() == "No helper available"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }
}
