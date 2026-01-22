package io.micronaut.python.annotation.processing.test.inject

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.python.processing.PythonAstParser
import io.micronaut.python.processing.PythonProcessingEnvironment
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

    void "script-level function docstrings are parsed and functions/attributes are exposed on PythonScriptElement"() {
        given:
        // Script with a documented function and a documented attribute
        def pythonCode = '''
from micronaut.context.annotation import Executable

message: str = "hello"

@Executable
def say_hello(name: str) -> str:
    """
    Say hello.

    Parameters:
        name (str): The person's name.

    Returns:
        str: The greeting.
    """
    return f"Hello, {name}!"
'''

        when:
        def parser = new PythonAstParser()
        def env = parser.parse(pythonCode, "python")
        def processingEnv = new PythonProcessingEnvironment(env, null)
        // Obtain the script element (there will be exactly one for this parse)
        ClassElement scriptElement = processingEnv.scripts().values().iterator().next()

        and:
        // Resolve method and property from the script element
        MethodElement method = scriptElement.getEnclosedElements(ElementQuery.ALL_METHODS.named("say_hello")).first()
        PropertyElement messageProp = scriptElement.getBeanProperties().find { it.name == 'message' }

        then: "Function documentation is parsed and available"
        method != null
        method.getDocumentation(false).isPresent()
        method.getDocumentation(false).get().contains("Say hello.")
        // Parsed documentation should exclude structured sections like Parameters/Returns
        method.getDocumentation(true).isPresent()
        !method.getDocumentation(true).get().contains("Parameters:")
        !method.getDocumentation(true).get().contains("Returns:")

        and: "Parameter documentation is available"
        method.parameters.length == 1
        method.parameters[0].getDocumentation(false).isPresent()
        method.parameters[0].getDocumentation(false).get().toLowerCase().contains("person's name")

        and: "Script attributes are exposed as properties on the script element"
        messageProp != null
        messageProp.getReadType().get().name == String.name
        // Attribute-level docstrings are not currently captured at script scope; documentation is empty
        messageProp.getDocumentation(true).isEmpty()

        cleanup:
        env?.close()
    }
}
