package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class MethodInjectionSpec extends AbstractPythonTypeElementSpec {

    void "test method injection with @Inject annotation"() {
        given: "Python code with method injection"
        def pythonCode = '''
from jakarta.inject import Singleton, Inject
from io.micronaut.context.annotation import Executable

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
}
