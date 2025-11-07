package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import spock.lang.PendingFeature

class FieldInjectionSpec extends AbstractPythonTypeElementSpec {

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
}
