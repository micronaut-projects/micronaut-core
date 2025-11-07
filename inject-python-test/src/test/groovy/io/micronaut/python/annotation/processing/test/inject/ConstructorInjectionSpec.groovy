package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class ConstructorInjectionSpec extends AbstractPythonTypeElementSpec {

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
