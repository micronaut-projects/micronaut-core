package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.http.client.annotation.Client
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.validation.Constraint
import jakarta.validation.constraints.NotNull

class ConstructorInjectionSpec extends AbstractPythonTypeElementSpec {
    void "test annotated constructor injection - imported type"() {
        given: "Python code with constructor injection"
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from jakarta.validation.constraints import NotNull
from micronaut.context.annotation import Executable
from typing import Annotated

@Singleton
class MainService:
    def __init__(self, client : Annotated[HttpClient, Client("/")]):
        self.client = client

    @Executable
    def get_message(self) -> str:
        if self.client is not None:
            return "has client"
        else:
            return "has not client"
'''

        when: "Building ApplicationContext and getting the bean definition"
        def context = buildContext(pythonCode, true)
        context.getBean(EmbeddedServer).start()

        def definition = getBeanDefinition(context, "python.MainService")
        def bean = getBean(context, "python.MainService")

        then: "Type should be resolved correctly"
        // Check that the type annotation is correctly parsed
        def argType = definition.constructor.arguments[0].type
        argType.name == 'io.micronaut.http.client.HttpClient'
        definition.constructor.arguments[0].getAnnotationMetadata().stringValue(Client).get() == '/'
        bean.get_message() == "has client"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test annotated constructor injection"() {
        given: "Python code with constructor injection"
        def pythonCode = '''
from jakarta.inject import Singleton
from jakarta.validation.constraints import NotNull
from micronaut.context.annotation import Executable
from typing import Annotated

@Singleton
class DependencyService:
    @Executable
    def get_message(self) -> str:
        return "Hello from dependency"

@Singleton
class MainService:
    def __init__(self, dependency: Annotated[DependencyService, NotNull(message="test")]):
        self.dependency = dependency

    @Executable
    def get_combined_message(self) -> str:
        return self.dependency.get_message() + " and main service"
'''

        when: "Building ApplicationContext and getting the main service"
        def context = buildContext(pythonCode)
        def mainService = getBean(context, "python.MainService")
        def defnition = getBeanDefinition(context, "python.MainService")

        then: "Constructor injection should work"
        mainService.get_combined_message() == "Hello from dependency and main service"
        defnition.constructor.arguments[0].getAnnotationMetadata().stringValue(NotNull, "message").get() == 'test'
        defnition.constructor.arguments[0].getAnnotationMetadata().getAnnotationNamesByStereotype(Constraint) == [NotNull.name]

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test constructor injection another Python type"() {
        given: "Python code with constructor injection"
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class DependencyService:
    @Executable
    def get_message(self) -> str:
        return "Hello from dependency"

@Singleton
class MainService:
    def __init__(self, dependency: DependencyService):
        self.dependency = dependency

    @Executable
    def get_combined_message(self) -> str:
        return self.dependency.get_message() + " and main service"
'''

        when: "Building ApplicationContext and getting the main service"
        def context = buildContext(pythonCode)
        def mainService = getBean(context, "python.MainService")

        then: "Constructor injection should work"
        mainService.get_combined_message() == "Hello from dependency and main service"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }
}
