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
from micronaut.python.compiler import RepeatableAnnotation

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
from micronaut.python.compiler import PrimitiveTypesAnnotation

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
from micronaut.context.annotation import Executable

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
from micronaut.context.annotation import Executable
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

}
