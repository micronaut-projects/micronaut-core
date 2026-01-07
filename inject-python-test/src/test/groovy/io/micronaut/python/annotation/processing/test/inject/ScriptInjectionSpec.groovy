package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.runtime.server.EmbeddedServer

class ScriptInjectionSpec extends AbstractPythonTypeElementSpec {
    void "test that visiting a script will produce a Script class that allows dependency injection the script"() {
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

server : Annotated[EmbeddedServer, Inject]
client : Annotated[HttpClient, Inject, Client("/")]

@Executable
def get_message() -> str:
    if client is not None and server is not None:
        return "has client and server"
    else:
        return "has not client"
'''

        when: "Building ApplicationContext and getting the bean definition"
        def context = buildContext(pythonCode, true) // Don't include all beans to avoid missing dependencies
        def definition = getBeanDefinition(context, "python.Script")
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
        def bean = getBean(context, "python.Script")

        then:
        bean.get_message() == "has client and server"

        cleanup: "Ensure context is properly closed"
        context?.close()
    }
}
