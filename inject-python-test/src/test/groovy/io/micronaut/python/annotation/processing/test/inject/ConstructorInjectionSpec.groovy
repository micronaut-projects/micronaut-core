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

    void "test factory method injection with @Creator"() {
        given: "Python code with factory method injection"
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.core.annotation import Creator
from typing import Annotated
from dataclasses import dataclass
from micronaut.context.annotation import Executable

@dataclass
@Singleton
class Engine:
    cylinders: int

    @classmethod
    @Creator
    def get_default(cls) -> "Engine":
        return cls(8)

@Singleton
class CarService:
    def __init__(self, engine: Engine):
        self.engine = engine

    @Executable
    def get_engine_cylinders(self) -> int:
        return self.engine.cylinders
'''

        when: "Building ApplicationContext and getting the car service"
        def context = buildContext(pythonCode)
        def carService = getBean(context, "python.CarService")

        then: "Factory method injection should work"
        carService.get_engine_cylinders() == 8

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test constructor injection with nullable argument"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from dataclasses import dataclass
from micronaut.context.annotation import Executable

@dataclass
class Engine:
    cylinders: int

    def start(self) -> str:
        return f"Vrooom! {self.cylinders}"

@Singleton
class Vehicle:
    def __init__(self, engine: Engine | None):
        self.engine = engine if engine is not None else Engine(6)

    @Executable
    def start(self) -> str:
        return self.engine.start()

'''
        when: "Building ApplicationContext and getting the car service"
        def context = buildContext(pythonCode)
        def carService = getBean(context, "python.Vehicle")

        then: "Factory method injection should work"
        carService.start() == "Vrooom! 6"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test constructor injection with typed Dict container type"() {
        given: "Python code with constructor injection using Dict type"
        def pythonCode = '''
from typing import Dict, Annotated
from jakarta.inject import Singleton, Inject, Named
from micronaut.context.annotation import Executable

class ServiceBase:
    @Executable
    def get_name(self) -> str:
        return "BaseService"

@Singleton
@Named("serviceA")
class ServiceA(ServiceBase):
    @Executable
    def get_name(self) -> str:
        return "ServiceA"

@Singleton
@Named("serviceB")
class ServiceB(ServiceBase):
    def get_name(self) -> str:
        return "ServiceB"

@Singleton
class DictConstructorService:
    def __init__(self, services: Annotated[Dict[str, ServiceBase], Inject]):
        self.services = services

    @Executable
    def get_service_count(self) -> int:
        if self.services is None:
            return 0
        return len(self.services)

    @Executable
    def get_service_names(self) -> str:
        if self.services is None:
            return "no services"
        names = [service.get_name() for service in self.services.values()]
        return ",".join(sorted(names))
'''

        when: "Building ApplicationContext and getting the dict constructor service"
        def context = buildContext(pythonCode)
        def dictService = getBean(context, "python.DictConstructorService")

        then: "Dict constructor injection should work"
        dictService.get_service_count() == 2
        dictService.get_service_names() == "ServiceA,ServiceB"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test constructor injection with typed List container type"() {
        given: "Python code with constructor injection using List type"
        def pythonCode = '''
from typing import List, Annotated
from jakarta.inject import Singleton, Inject
from micronaut.context.annotation import Executable

class ListItemService:
    def __init__(self, name: str):
        self.name = name

    def get_name(self) -> str:
        return self.name

@Singleton
class ItemA(ListItemService):
    def __init__(self):
        super().__init__("ItemA")

@Singleton
class ItemB(ListItemService):
    def __init__(self):
        super().__init__("ItemB")

@Singleton
class ListConstructorService:
    def __init__(self, items: Annotated[List[ListItemService], Inject]):
        self.items = items

    @Executable
    def get_item_count(self) -> int:
        if self.items is None:
            return 0
        return len(self.items)

    @Executable
    def get_item_names(self) -> str:
        if self.items is None:
            return "no items"
        names = [item.get_name() for item in self.items]
        return ",".join(sorted(names))
'''

        when: "Building ApplicationContext and getting the list constructor service"
        def context = buildContext(pythonCode)
        def listService = getBean(context, "python.ListConstructorService")

        then: "List constructor injection should work"
        listService.get_item_count() == 2
        listService.get_item_names() == "ItemA,ItemB"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }
}
