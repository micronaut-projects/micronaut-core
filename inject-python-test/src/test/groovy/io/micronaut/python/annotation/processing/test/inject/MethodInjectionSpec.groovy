package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class MethodInjectionSpec extends AbstractPythonTypeElementSpec {

    void "test method injection with @Inject annotation"() {
        given: "Python code with method injection"
        def pythonCode = '''
from jakarta.inject import Singleton, Inject
from micronaut.context.annotation import Executable

@Singleton
class HelperService:
    @Executable
    def get_help(self) -> str:
        return "I am helping!"

@Singleton
class MainService:
    def __init__(self):
        self.helper = None

    @Inject
    def set_helper(self, helper: HelperService):
        self.helper = helper

    @Executable
    def get_combined_message(self) -> str:
        if self.helper is None:
            return "No helper available"
        return "Main service with: " + self.helper.get_help()
'''

        when: "Building ApplicationContext and getting the main service"
        def context = buildContext(pythonCode)
        def mainService = getBean(context, "python.MainService")

        then: "Method injection should work"
        mainService.get_combined_message() == "Main service with: I am helping!"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test method injection with typed Dict container type"() {
        given: "Python code with method injection using Dict type"
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
class DictMethodService:
    def __init__(self):
        self.services = None

    @Inject
    def set_services(self, services: Annotated[Dict[str, ServiceBase], Inject]):
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

        when: "Building ApplicationContext and getting the dict method service"
        def context = buildContext(pythonCode)
        def dictService = getBean(context, "python.DictMethodService")

        then: "Dict method injection should work"
        dictService.get_service_count() == 2
        dictService.get_service_names() == "ServiceA,ServiceB"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    void "test method injection with typed List container type"() {
        given: "Python code with method injection using List type"
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
class ListMethodService:
    def __init__(self):
        self.items = None

    @Inject
    def set_items(self, items: Annotated[List[ListItemService], Inject]):
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

        when: "Building ApplicationContext and getting the list method service"
        def context = buildContext(pythonCode)
        def listService = getBean(context, "python.ListMethodService")

        then: "List method injection should work"
        listService.get_item_count() == 2
        listService.get_item_names() == "ItemA,ItemB"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }
}
