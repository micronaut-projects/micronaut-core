/*
 * Copyright 2017-2025 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test

import io.micronaut.context.python.ContextHolder
import io.micronaut.inject.ast.ClassElement
import io.micronaut.python.compiler.PrimitiveTypesAnnotation
import io.micronaut.python.compiler.RepeatableAnnotation
import spock.lang.PendingFeature
import spock.lang.Unroll

/**
 * Tests for Python class elements.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class PythonClassElementSpec extends AbstractPythonTypeElementSpec {

    def "test build class element from Python source"() {
        given:
        def pythonCode = '''
class TestClass:
    def hello(self):
        return "Hello from Python!"
'''

        when:
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            // Verify the class element was created
            assert element != null
            assert element.getSimpleName() == "TestClass"
            return element
        }

        then:
        classElement != null
        classElement.getSimpleName() == "TestClass"
    }

    def "test build class element with methods"() {
        given:
        def pythonCode = '''
class MyService:
    def __init__(self):
        self.value = "test"

    def get_value(self):
        return self.value

    def set_value(self, value):
        self.value = value
'''

        when:
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "MyService"
            return element
        }

        then:
        classElement != null
        classElement.getSimpleName() == "MyService"
    }

    def "test stereotype annotations are correctly resolved"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton

@Singleton
class MySingletonService:
    pass
'''

        when:
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "MySingletonService"

            // Verify the direct annotation is present
            assert element.hasAnnotation("jakarta.inject.Singleton")
            // Verify the stereotype annotation is present
            assert element.hasStereotype("jakarta.inject.Scope")
            // Verify that Scope is not directly annotated (only present as stereotype)
            assert !element.hasAnnotation("jakarta.inject.Scope")
            return element
        }

        then:
        classElement != null
        classElement.getSimpleName() == "MySingletonService"
    }

    def "test repeatable annotations are correctly resolved"() {
        given:
        def pythonCode = '''
from io.micronaut.python.compiler import RepeatableAnnotation

@RepeatableAnnotation("first")
@RepeatableAnnotation("second")
@RepeatableAnnotation("third")
class MyAnnotatedService:
    pass
'''

        when:
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "MyAnnotatedService"

            // Get all RepeatableAnnotation values
            def annotations = element.getAnnotationValuesByType(RepeatableAnnotation)
            assert annotations.size() == 3

            // Verify the values are correct
            def values = annotations*.stringValues().flatten().sort()
            assert values == ["first", "second", "third"]

            return element
        }

        then:
        classElement != null
        classElement.getSimpleName() == "MyAnnotatedService"
    }

    def "test annotation primitive types and arrays"() {
        given:
        def pythonCode = '''
from io.micronaut.python.compiler import PrimitiveTypesAnnotation

@PrimitiveTypesAnnotation(
    booleanValue=True,
    byteValue=42,
    charValue='A',
    doubleValue=3.14,
    floatValue=2.71,
    intValue=100,
    longValue=1000,
    shortValue=10,
    booleanArray=[True, False],
    byteArray=[1, 2, 3],
    charArray=['A', 'B', 'C'],
    doubleArray=[1.1, 2.2],
    floatArray=[1.1, 2.2],
    intArray=[10, 20, 30],
    longArray=[100, 200],
    shortArray=[1, 2, 3],
    stringValue="test",
    stringArray=["hello", "world"],
    classValue=str,
    classArray=[str, int]
)
class MyPrimitiveAnnotatedService:
    pass
'''

        when:
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "MyPrimitiveAnnotatedService"

            // Get the annotation
            def annotation = element.getAnnotation(PrimitiveTypesAnnotation)
            assert annotation != null

            // Test primitive values
            assert annotation.booleanValue("booleanValue").get() == true
            assert annotation.byteValue("byteValue").get() == 42
            assert annotation.charValue("charValue").get() == 'A'
            assert annotation.doubleValue("doubleValue").getAsDouble() == 3.14d
            assert annotation.floatValue("floatValue").get() == 2.71f
            assert annotation.intValue("intValue").asInt == 100
            assert annotation.longValue("longValue").getAsLong() == 1000L
            assert annotation.shortValue("shortValue").get() == 10

            // Test primitive arrays
            assert annotation.booleanValues("booleanArray") == [true, false] as boolean[]
            assert annotation.byteValues("byteArray") == [1, 2, 3] as byte[]
            assert annotation.charValues("charArray") == ['A', 'B', 'C'] as char[]
            assert annotation.doubleValues("doubleArray") == [1.1, 2.2] as double[]
            assert annotation.floatValues("floatArray") == [1.1f, 2.2f] as float[]
            assert annotation.intValues("intArray") == [10, 20, 30] as int[]
            assert annotation.longValues("longArray") == [100L, 200L] as long[]
            assert annotation.shortValues("shortArray") == [1, 2, 3] as short[]

            // Test string and string array
            assert annotation.stringValue("stringValue").get() == "test"
            assert annotation.stringValues("stringArray") == ["hello", "world"] as String[]

            // Test class and class array
            assert annotation.annotationClassValue("classValue").get().name == String.name
            assert annotation.annotationClassValues("classArray")*.name == [String.class.name, Integer.class.name]

            return element
        }

        then:
        classElement != null
        classElement.getSimpleName() == "MyPrimitiveAnnotatedService"
    }

    def "test ApplicationContext can be started from generated Python script"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from io.micronaut.context.annotation import Executable

@Singleton
class SimpleService:
    @Executable
    def greet(self, name : str) -> str:
        return f"Hello, {name}!"

    def get_count(self):
        return 42
'''

        when: "Building ApplicationContext from Python code"
        def context = buildContext(pythonCode)
        def javaStub = context.classLoader.loadClass("python.SimpleService")

        then: "Context should start successfully"
        context != null
        context.isRunning()
        javaStub != null
        context.getBean(javaStub).greet("John") == "Hello, John!"

        ContextHolder.isInitialized()
        ContextHolder.getContext() != null
        ContextHolder.context.getBindings("python").getMember("SimpleService") != null


        cleanup: "Ensure context is properly closed"
        context?.close()
    }

    def "test ApplicationContext cleanup resets ContextHolder"() {
        given:
        def pythonCode = '''
class TestService:
    def ping(self):
        return "pong"
'''

        when: "Building and closing ApplicationContext"
        def context = buildContext(pythonCode)
        context.close()

        then: "ContextHolder should be reset"
        !ContextHolder.isInitialized()

        and: "Accessing context after cleanup should throw exception"
        when:
        ContextHolder.getContext()
        then:
        thrown(IllegalStateException)
    }

    @Unroll
    def "test different return types: #description"() {
        given: "Python code with specific return type annotation"
        def pythonCode = """
from jakarta.inject import Singleton
from io.micronaut.context.annotation import Executable
from typing import Optional

@Singleton
class TypeTestService:
    @Executable
    def get_value(self) -> $pythonTypeAnnotation:
        return $pythonValue
"""

        when: "Building ApplicationContext and calling method"
        def context = buildContext(pythonCode)
        def result = getBean(context, "python.TypeTestService").get_value()
        def beanDefinition = getBeanDefinition(context, "python.TypeTestService")

        then: "Result should be correctly converted to expected type"
        result == expectedValue
        result?.getClass() == expectedType
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].returnType.type == returnType

        cleanup: "Ensure context is properly closed"
        context?.close()

        where:
        description           | pythonTypeAnnotation | pythonValue | expectedValue        | expectedType    | returnType
        "Dict return type"    | "dict[str, int]"     | '{"a": 1}'  | ["a": 1]             | HashMap.class   | Map.class
        "Optional present"    | "Optional[str]"      | '"Alice"'   | Optional.of("Alice") | Optional.class  | Optional.class
        "Optional empty"      | "Optional[str]"      | "None"      | Optional.empty()     | Optional.class  | Optional.class
        "None return type"    | "None"               | "None"      | null                 | null            | void.class
        "List return type"    | "list[int]"          | "[1, 2, 3]" | [1, 2, 3]            | ArrayList.class | List.class
        "String return type"  | "str"                | '"hello"'   | "hello"              | String.class    | String.class
        "Integer return type" | "int"                | "42"        | 42                   | Integer         | Integer.TYPE
        "Boolean True"        | "bool"               | "True"      | true                 | Boolean         | Boolean.TYPE
        "Boolean False"       | "bool"               | "False"     | false                | Boolean         | Boolean.TYPE
        "Float return type"   | "float"              | "3.14"      | 3.14d                | Double          | Double.TYPE

    }

    def "test documentation survives full processing pipeline"() {
        given: "Python code with comprehensive documentation"
        def pythonCode = '''
class DocumentedClass:
    """This is a class with documentation.

    This class demonstrates various documentation features
    that should survive the full processing pipeline.
    """
    def __init__(self, name: str, age: int = 25):
        """Initialize the documented class.

        Args:
            name (str): The name of the instance
            age (int): The age of the instance, defaults to 25
        """
        self.name = name
        self.age = age

    def documented_method(self, param1: str, param2: int = 10) -> bool:
        """A method with parameter documentation.

        This method demonstrates parameter documentation extraction
        that should survive transformation.

        Args:
            param1 (str): The first parameter description
            param2 (int): The second parameter with default

        Returns:
            bool: Always returns True
        """
        return True

    undocumented_field = "no docs"
    documented_field: str = "has docs"
    """This field has documentation."""
'''

        when: "Processing through full pipeline including micronaut_transformer.py"
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            return element
        }

        then: "Documentation should be preserved after full processing"
        classElement != null
        classElement.getSimpleName() == "DocumentedClass"

        and: "Class documentation should be accessible"
        def classDoc = classElement.getDocumentation(true)
        classDoc.isPresent()
        classDoc.get().contains("This is a class with documentation")
        classDoc.get().contains("This class demonstrates various documentation features")

        and: "Method documentation should be accessible"
        def methods = classElement.getEnclosedElements(io.micronaut.inject.ast.ElementQuery.ALL_METHODS)
        def method = methods.find { it.getName() == "documented_method" }
        method != null

        def methodDoc = method.getDocumentation(true)
        methodDoc.isPresent()
        methodDoc.get().contains("A method with parameter documentation")
        methodDoc.get().contains("This method demonstrates parameter documentation extraction")

        and: "Parameter documentation should be accessible"
        def params = method.getParameters()
        params.length == 2

        def param1Doc = params[0].getDocumentation(false)
        param1Doc.isPresent()
        param1Doc.get().trim() == "The first parameter description"

        def param2Doc = params[1].getDocumentation(false)
        param2Doc.isPresent()
        param2Doc.get().trim() == "The second parameter with default"

        and: "Constructor parameter documentation should be accessible"
        def constructor = classElement.getPrimaryConstructor().orElse(null)
        constructor != null

        def constructorParams = constructor.getParameters()
        constructorParams.length == 2

        def nameParamDoc = constructorParams[0].getDocumentation(false)
        nameParamDoc.isPresent()
        nameParamDoc.get().trim() == "The name of the instance"

        def ageParamDoc = constructorParams[1].getDocumentation(false)
        ageParamDoc.isPresent()
        ageParamDoc.get().trim() == "The age of the instance, defaults to 25"

        and: "Field documentation should be accessible"
        def fields = classElement.getFields()
        def documentedField = fields.find { it.getName() == "documented_field" }
        documentedField != null

        def fieldDoc = documentedField.getDocumentation(false)
        fieldDoc.isPresent()
        fieldDoc.get().trim() == "This field has documentation."
    }

    def "test documentation survives processing with annotation transformations"() {
        given: "Python code with documentation AND Java annotation imports that get transformed"
        def pythonCode = '''
from jakarta.inject import Singleton
from typing import Annotated

@Singleton
class AnnotatedDocumentedClass:
    """A class with both annotations and documentation.

    This tests that documentation survives the micronaut_transformer.py
    transformations that convert Java imports to decorators.
    """

    def __init__(self, config: Annotated[str, "Configuration value"]):
        """Initialize with annotated parameter.

        Args:
            config: A configuration value with annotation metadata
        """
        self.config = config

    def annotated_method(self, param: Annotated[str, "Some annotation"]) -> str:
        """Method with annotated parameters.

        Args:
            param: A parameter with annotation metadata that should survive transformation

        Returns:
            str: The processed parameter
        """
        return param
'''

        when: "Processing through full pipeline (includes micronaut_transformer.py)"
        def classElement = buildClassElement(pythonCode) { ClassElement element ->
            return element
        }

        then: "Both annotations and documentation should work after transformation"
        classElement != null
        classElement.getSimpleName() == "AnnotatedDocumentedClass"

        and: "Annotation should be correctly resolved"
        classElement.hasAnnotation("jakarta.inject.Singleton")

        and: "Class documentation should survive transformation"
        def classDoc = classElement.getDocumentation(true)
        classDoc.isPresent()
        classDoc.get().contains("A class with both annotations and documentation")
        classDoc.get().contains("micronaut_transformer.py")

        and: "Method documentation should survive transformation"
        def method = classElement.getEnclosedElements(io.micronaut.inject.ast.ElementQuery.ALL_METHODS)
                .find { it.getName() == "annotated_method" }
        method != null

        def methodDoc = method.getDocumentation(true)
        methodDoc.isPresent()
        methodDoc.get().contains("Method with annotated parameters")

        and: "Parameter documentation should survive transformation"
        def params = method.getParameters()
        params.length == 1

        def paramDoc = params[0].getDocumentation(false)
        paramDoc.isPresent()
        paramDoc.get().trim().contains("A parameter with annotation metadata")
    }


}
