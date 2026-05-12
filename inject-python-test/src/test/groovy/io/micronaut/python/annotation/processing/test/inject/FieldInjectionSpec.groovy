package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer

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
        def serverSetter = definition.injectedMethods.find { it.methodName == 'setServer' }
        def clientSetter = definition.injectedMethods.find { it.methodName == 'setClient' }

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

    void "test field injection with typed Dict container type"() {
        given: "Python code with field injection using Dict type"
        def pythonCode = '''
from typing import Dict, Annotated
from jakarta.inject import Singleton, Inject, Named
from micronaut.context.annotation import Executable

class ServiceBase:
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
    @Executable
    def get_name(self) -> str:
        return "ServiceB"

@Singleton
class ContainerService:
    services: Annotated[Dict[str, ServiceBase], Inject] = None

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

        when: "Building ApplicationContext and getting the container service"
        def context = buildContext(pythonCode)
        def containerService = getBean(context, "python.ContainerService")

        then: "Dict injection should work"
        containerService.get_service_count() == 2
        containerService.get_service_names() == "ServiceA,ServiceB"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test field injection with typed List container type"() {
        given: "Python code with field injection using List type"
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
class ListConsumerService:
    items: Annotated[List[ListItemService], Inject] = None

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

        when: "Building ApplicationContext and getting the list consumer service"
        def context = buildContext(pythonCode)
        def listConsumerService = getBean(context, "python.ListConsumerService")

        then: "List injection should work"
        listConsumerService.get_item_count() == 2
        listConsumerService.get_item_names() == "ItemA,ItemB"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test field injection with named qualifiers"() {
        given: "Python code with named qualifier field injection"
        def pythonCode = '''
from typing import Annotated
from jakarta.inject import Singleton, Named, Inject
from micronaut.context.annotation import Executable

class Thing:
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
    thing_one: Annotated[Thing, Inject, Named("one")] = None
    thing_two: Annotated[Thing, Inject, Named("two")] = None

    @Executable
    def get_thing_one_name(self) -> str:
        if self.thing_one is None:
            return "None"
        return self.thing_one.get_name()

    @Executable
    def get_thing_two_name(self) -> str:
        if self.thing_two is None:
            return "None"
        return self.thing_two.get_name()
'''

        when: "Building ApplicationContext and getting the named qualifier service"
        def context = buildContext(pythonCode)
        def service = getBean(context, "python.NamedQualifierService")

        then: "Named qualifier field injection should work"
        service.get_thing_one_name() == "one"
        service.get_thing_two_name() == "two"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }
}
