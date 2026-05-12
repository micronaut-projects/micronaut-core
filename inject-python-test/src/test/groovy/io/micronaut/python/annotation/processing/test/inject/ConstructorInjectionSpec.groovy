package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.context.BeanProvider
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

    void "test constructor injection with bean provider"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

import java

BeanProvider = java.type("io.micronaut.context.BeanProvider")

class Engine:
    @Executable
    def start(self) -> str:
        return "base"

@Singleton
class V8Engine(Engine):
    @Executable
    def start(self) -> str:
        return "Vrooom! 8"

@Singleton
class Vehicle:
    def __init__(self, engine: BeanProvider[Engine]):
        self.engine = engine

    @Executable
    def start(self) -> str:
        return self.engine.get().start()

'''
        when: "Building ApplicationContext and getting the car service"
        def context = buildContext(pythonCode)
        def carService = getBean(context, "python.Vehicle")
        def definition = getBeanDefinition(context, "python.Vehicle")
        def providerArgument = definition.constructor.arguments[0]

        then: "BeanProvider constructor injection should retain its generic bean type"
        providerArgument.type == BeanProvider
        providerArgument.typeParameters[0].type.name == "python.Engine"
        definition.requiredComponents.contains(context.classLoader.loadClass("python.Engine"))
        carService.start() == "Vrooom! 8"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test constructor injection with jakarta provider"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

import java

Provider = java.type("jakarta.inject.Provider")

class Engine:
    @Executable
    def start(self) -> str:
        return "base"

@Singleton
class V8Engine(Engine):
    @Executable
    def start(self) -> str:
        return "Vrooom! 8"

@Singleton
class Vehicle:
    def __init__(self, engine: Provider[Engine]):
        self.engine = engine

    @Executable
    def start(self) -> str:
        return self.engine.get().start()

'''
        when:
        def context = buildContext(pythonCode)
        def carService = getBean(context, "python.Vehicle")
        def definition = getBeanDefinition(context, "python.Vehicle")
        def providerArgument = definition.constructor.arguments[0]

        then:
        providerArgument.type.name == "jakarta.inject.Provider"
        providerArgument.typeParameters[0].type.name == "python.Engine"
        providerArgument.isProvider()
        definition.requiredComponents.contains(context.classLoader.loadClass("python.Engine"))
        carService.start() == "Vrooom! 8"

        cleanup:
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

    void "test constructor injection list excludes abstract beans"() {
        given:
        def context = buildContext('''
from abc import ABC, abstractmethod
from typing import List
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

class InterceptRule(ABC):
    @abstractmethod
    def name(self) -> str:
        pass

@Singleton
class AbstractBean(InterceptRule, ABC):
    @abstractmethod
    def name(self) -> str:
        pass

@Singleton
class ConcreteBean(InterceptRule):
    @Executable
    def name(self) -> str:
        return "concrete"

@Singleton
class Test:
    def __init__(self, rules: List[InterceptRule]):
        self.rules = rules

    @Executable
    def rule_count(self) -> int:
        return len(self.rules)

    @Executable
    def rule_names(self) -> str:
        return ",".join(sorted([rule.name() for rule in self.rules]))
''')

        when:
        def bean = getBean(context, "python.Test")

        then:
        bean.rule_count() == 1
        bean.rule_names() == "concrete"

        cleanup:
        context?.close()
    }

    void "test constructor injection with named qualifiers"() {
        given: "Python code with named qualifier injection"
        def pythonCode = '''
from jakarta.inject import Singleton, Named
from micronaut.context.annotation import Executable
from typing import Annotated

class Thing:
    @Executable
    def get_name(self) -> str:
        return "Thing"

@Singleton
@Named("one")
class ThingOne(Thing):
    @Executable
    def get_name(self) -> str:
        return "one"

@Singleton
@Named("two")
class ThingTwo(Thing):
    @Executable
    def get_name(self) -> str:
        return "two"

@Singleton
class NamedQualifierService:
    def __init__(self, thing_one: Annotated[Thing, Named("one")], thing_two: Annotated[Thing, Named("two")]):
        self.thing_one = thing_one
        self.thing_two = thing_two

    @Executable
    def get_thing_one_name(self) -> str:
        return self.thing_one.get_name()

    @Executable
    def get_thing_two_name(self) -> str:
        return self.thing_two.get_name()
'''

        when: "Building ApplicationContext and getting the named qualifier service"
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.NamedQualifierService")

        then: "Named qualifier injection should work"
        service.get_thing_one_name() == "one"
        service.get_thing_two_name() == "two"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test constructor injection with annotation qualifiers"() {
        given: "Python code with named qualifier injection"
        def pythonCode = '''
from jakarta.inject import Singleton, Named
from micronaut.context.annotation import Executable
from typing import Annotated
from jakarta.inject.Qualifier import Qualifier

def micronaut_annotation(name):
    def decorator(func):
        return func
    return decorator

@micronaut_annotation("One")
@Qualifier
def One(func):
    return func

@micronaut_annotation("Two")
@Qualifier
def Two(func):
    return func

class Thing:
    def get_name(self) -> str:
        return "Thing"

@Singleton
class OneThing(Thing):
    def get_name(self) -> str:
        return "one"

@Singleton
class TwoThing(Thing):
    def get_name(self) -> str:
        return "two"

@Singleton
class NamedQualifierService:
    def __init__(self, thing_one: Annotated[Thing, One], thing_two: Annotated[Thing, Two]):
        self.thing_one = thing_one
        self.thing_two = thing_two

    @Executable
    def get_thing_one_name(self) -> str:
        return self.thing_one.get_name()

    @Executable
    def get_thing_two_name(self) -> str:
        return self.thing_two.get_name()
'''

        when: "Building ApplicationContext and getting the named qualifier service"
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.NamedQualifierService")

        then: "Named qualifier injection should work"
        service.get_thing_one_name() == "one"
        service.get_thing_two_name() == "two"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test constructor injection with non binding annotation member qualifier"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton, Qualifier
from micronaut.context.annotation import NonBinding
from typing import Annotated

@Qualifier
def Cylinders(value: int, description: Annotated[str, NonBinding] = ""):
    def decorator(func):
        return func
    return decorator

class Engine:
    def start(self) -> str:
        return "Engine"

@Singleton
@Cylinders(value=6, description="6-cylinder V6 engine")
class V6Engine(Engine):
    def start(self) -> str:
        return "Starting V6"

@Singleton
@Cylinders(value=8, description="8-cylinder V8 engine")
class V8Engine(Engine):
    def start(self) -> str:
        return "Starting V8"

@Singleton
class Vehicle:
    def __init__(self, engine: Annotated[Engine, Cylinders(value=8)]):
        self.engine = engine

    def start(self) -> str:
        return self.engine.start()
'''

        when:
        def context = buildContext(pythonCode)
        def vehicle = getBean(context, "python.Vehicle").asPolyglotValue()

        then:
        vehicle.invokeMember("start").asString() == "Starting V8"

        cleanup:
        context?.close()
    }
}
