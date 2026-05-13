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
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.aop.internal.intercepted.InterceptedMethodUtil
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Mapper
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.expressions.EvaluatedExpressionReference
import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.python.compiler.PrimitiveTypesAnnotation
import io.micronaut.python.compiler.RepeatableAnnotation
import spock.lang.PendingFeature
import spock.lang.Unroll

import java.util.function.Function

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

    def "test class element exposes generated Python name"() {
        expect:
        buildClassElement('''
class Test:
    pass
''') { ClassElement element ->
            assert element.name == "python.Test"
            assert element.canonicalName == "python.Test"
            assert element.simpleName == "Test"
            return element
        }
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

    def "test constant references resolve to correct value in annotation metadata"() {
        expect:
        buildClassElement('''
from jakarta.inject import Named
from micronaut.core.util import StringUtils

@Named(StringUtils.TRUE)
class MySingletonService:
    pass
''') { ClassElement element ->
            assert element != null
            assert element.stringValue(AnnotationUtil.NAMED).get() == "true"
            return element
        }
    }

    def "test keyword-safe import segments resolve annotations"() {
        expect:
        buildClassElement('''
from micronaut.core.async_.annotation import SingleResult

class MyController:
    @SingleResult
    def stream(self) -> object:
        pass
''') { ClassElement element ->
            def streamMethod = element.getMethods().find { it.name == "stream" }
            assert streamMethod != null
            assert streamMethod.hasAnnotation("io.micronaut.core.async.annotation.SingleResult")
            return element
        }
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

    def "test getDeclaredGenericPlaceholders returns type variables"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar

T = TypeVar('T')

class MyBase(Generic[T]):
    pass
'''

        expect:
        buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "MyBase"
            def placeholders = element.getDeclaredGenericPlaceholders()
            assert placeholders.size() == 1
            def placeholder = placeholders[0]
            assert placeholder.getVariableName() == "T"

            return element
        }
    }


    def "test generic type arguments populated from actual types in inheritance"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar

T = TypeVar('T')

class MyBase(Generic[T]):
    def echo(val : T) -> T:
        return val

class MyDerived(MyBase[str]):
    pass
'''

        expect:
        // Test that we can build classes with generic base classes without errors
        buildClassElement(pythonCode, "MyDerived") { ClassElement element ->
            // Test that getSuperType() works correctly
            def superType = element.getSuperType()
            assert superType.isPresent()
            assert superType.get().getSimpleName() == "MyBase"
            assert superType.get().typeArguments["T"].name == String.name
            def methods = element.getMethods()
            assert methods.size() == 1
            assert methods[0].genericReturnType.name == String.name
            assert methods[0].returnType.name == Object.name
            assert methods[0].parameters[0].genericType.name == String.name
            assert methods[0].parameters[0].type.name == Object.name
            return element
        }
    }

    def "test generic type arguments populated java interface in inheritance"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar
import java

Function = java.type("java.util.function.Function")

class MyFunction(Function[str, str]):
    def apply(val : str) -> str:
        return val
'''

        expect:
        // Test that we can build classes with generic base classes without errors
        buildClassElement(pythonCode, "MyFunction") { ClassElement element ->
            // Test that getSuperType() works correctly
            def superType = element.getSuperType()
            assert !superType.isPresent()
            def interfaces = element.getInterfaces()
            assert !interfaces.isEmpty()
            assert interfaces.size() == 1
            def javaFnc = interfaces.iterator().next()
            assert javaFnc.name == Function.name
            assert javaFnc.typeArguments['T'].name == String.name
            assert javaFnc.typeArguments['R'].name == String.name
            return element
        }
    }

    def "test generic type arguments populated java interface using import"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar
from java.util.function import Function

class MyFunction(Function[str, str]):
    def apply(val : str) -> str:
        return val
'''

        expect:
        // Test that we can build classes with generic base classes without errors
        buildClassElement(pythonCode, "MyFunction") { ClassElement element ->
            // Test that getSuperType() works correctly
            def superType = element.getSuperType()
            assert !superType.isPresent()
            def interfaces = element.getInterfaces()
            assert !interfaces.isEmpty()
            assert interfaces.size() == 1
            def javaFnc = interfaces.iterator().next()
            assert javaFnc.name == Function.name
            assert javaFnc.typeArguments['T'].name == String.name
            assert javaFnc.typeArguments['R'].name == String.name
            return element
        }
    }

    def "test nested java interface resolves from imported class attribute base"() {
        given:
        def pythonCode = '''
from typing import Any
from micronaut.context.annotation import Mapper

class MyMergeStrategy(Mapper.MergeStrategy):
    def merge(
        self,
        current_value: Any,
        value: Any,
        value_owner: Any,
        property_name: str,
        mapped_property_name: str,
    ) -> Any:
        return value
'''

        expect:
        buildClassElement(pythonCode, "MyMergeStrategy") { ClassElement element ->
            def superType = element.getSuperType()
            assert !superType.isPresent()
            def interfaces = element.getInterfaces()
            assert interfaces.size() == 1
            assert interfaces.iterator().next().name == Mapper.MergeStrategy.name
            return element
        }
    }

    def "test nested mapper annotations resolve from annotation array members"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from micronaut.context.annotation import Mapper
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class ChristmasPresent:
    packaging_color: str

@Introspected
@dataclass
class PresentPackaging:
    color: str

class ProductMappers(ABC):
    @Mapper(
        mergeStrategy="add-numbers",
        value=[Mapper.Mapping(**{"from": "packaging.color", "to": "packaging_color"})],
    )
    @abstractmethod
    def merge_with_merge_strategy(self, packaging: PresentPackaging, present: object) -> ChristmasPresent:
        pass
'''

        expect:
        buildClassElement(pythonCode, "ProductMappers") { ClassElement element ->
            def method = element.findMethod("merge_with_merge_strategy").get()
            def mappings = method.annotationMetadata.getAnnotationValuesByType(Mapper.Mapping)
            assert mappings.size() == 1
            assert mappings[0].stringValue("from").get() == "packaging.color"
            assert mappings[0].stringValue("to").get() == "packaging_color"
            return element
        }
    }

    def "test generic type arguments populated java interface as bean"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar
from java.util.function import Function
from jakarta.inject import Singleton

print(F" TYPE {Function[str, str]}")

@Singleton
class MyFunction(Function[str, str]):
    def apply(val : str) -> str:
        return val
'''

        def context = buildContext(pythonCode)

        expect:
        getBean(context, "python.MyFunction") instanceof Function
        // Test that we can build classes with generic base classes without errors
    }

    def "test override method with parameterized types"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar
import java

DataSource = java.type("javax.sql.DataSource")

class MyDataSource(DataSource):
    pass
'''

        expect:
        // Test that we can build classes with generic base classes without errors
        buildClassElement(pythonCode, "MyDataSource") { ClassElement element ->
            // Test that getSuperType() works correctly
            return element
        }
    }

    def "test generic type arguments populated from function return types and arguments"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar

T = TypeVar('T')

class MyGeneric(Generic[T]):
    pass

class MyClass:
    def echo(val : MyGeneric[str]) -> MyGeneric[str]:
        return val
'''

        expect:
        // Test that we can build classes with generic base classes without errors
        buildClassElement(pythonCode, "MyClass") { ClassElement element ->
            // Test that getSuperType() works correctly
            def methods = element.getMethods()
            assert methods.size() == 1
            assert methods[0].genericReturnType.name == "python.MyGeneric"
            assert methods[0].genericReturnType.getFirstTypeArgument().get().name == String.name
            assert methods[0].returnType.name == "python.MyGeneric"
            assert methods[0].parameters[0].genericType.name == "python.MyGeneric"
            assert methods[0].parameters[0].type.name == "python.MyGeneric"
            return element
        }
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0024")
    def "test nullability on generic return type arguments"() {
        given:
        def pythonCode = '''
from typing import Annotated
from java.util.concurrent import CompletionStage
from micronaut.core.annotation import NonNull, Nullable

class TypeTestService:
    def not_nullable_method(self) -> CompletionStage[Annotated[str, NonNull]]:
        pass

    def nullable_method(self) -> CompletionStage[Annotated[str, Nullable]]:
        pass

    def pep604_nullable_method(self) -> CompletionStage[str | None]:
        pass

    def method(self) -> CompletionStage[str]:
        pass
'''

        expect:
        buildClassElement(pythonCode, "TypeTestService") { ClassElement element ->
            def notNullableMethod = element.findMethod("not_nullable_method").get()
            def nullableMethod = element.findMethod("nullable_method").get()
            def pep604NullableMethod = element.findMethod("pep604_nullable_method").get()
            def method = element.findMethod("method").get()

            def notNullableType = notNullableMethod.genericReturnType.getFirstTypeArgument().get()
            assert notNullableType.isNonNull()
            assert !notNullableType.isNullable()

            def nullableType = nullableMethod.genericReturnType.getFirstTypeArgument().get()
            assert !nullableType.isNonNull()
            assert nullableType.isNullable()

            def pep604NullableType = pep604NullableMethod.genericReturnType.getFirstTypeArgument().get()
            assert !pep604NullableType.isNonNull()
            assert pep604NullableType.isNullable()

            def type = method.genericReturnType.getFirstTypeArgument().get()
            assert !type.isNonNull()
            assert !type.isNullable()
            return element
        }
    }

    @PendingFeature(reason = """
Function _parse_function_type_params in micronaut_processor.py cannot find type_params

FunctionDef https://docs.python.org/3/library/ast.html#ast.FunctionDef defines type_params but only since 3.12 so maybe a Python version issue.
""")
    def "test method-level type variables"() {
        given:
        def pythonCode = '''
from typing import TypeVar, List

S = TypeVar('S')

class Helper:
    def singleton_list(self, item: S) -> List[S]:
        return [item]
'''

        expect:
        buildClassElement(pythonCode, "Helper") { ClassElement element ->
            def methods = element.getMethods()
            println "Found ${methods.size()} methods"
            methods.each { println "Method: ${it.getName()}" }

            assert methods.size() == 1
            def method = methods[0]
            assert method.getName() == "singleton_list"

            // Check that the method has declared type variables
            def typeVars = method.getDeclaredTypeVariables()
            println "Found ${typeVars.size()} type variables"
            typeVars.each { println "Type var: ${it.getVariableName()}" }

            assert typeVars.size() == 1
            assert typeVars[0].getVariableName() == "S"

            return element
        }
    }

    def "test nested generic type arguments in base classes"() {
        given:
        def pythonCode = '''
from typing import Generic, TypeVar

T = TypeVar('T')

class MyBase(Generic[T]):
    def get_value(self) -> T:
        return None

class MyDerived(MyBase[dict[str, int]]):
    pass
'''

        expect:
        // Test that we can build classes with nested generic base classes without errors
        buildClassElement(pythonCode, "MyDerived") { ClassElement element ->
            // Test that getSuperType() works correctly
            def superType = element.getSuperType()
            assert superType.isPresent()
            assert superType.get().getSimpleName() == "MyBase"
            return element
        }
    }

    def "test method parameter dict generic type arguments"() {
        given:
        def pythonCode = '''
from typing import Any

class TypeTestService:
    def update(self, update_fields: dict[str, Any]) -> object:
        pass
'''

        expect:
        buildClassElement(pythonCode, "TypeTestService") { ClassElement element ->
            def method = element.findMethod("update").get()
            def parameter = method.parameters[0]
            def typeAnnotation = parameter.nativeType.typeAnnotation()
            def genericType = parameter.genericType
            def boundGenericTypes = genericType.boundGenericTypes

            assert typeAnnotation.name() == "dict"
            assert typeAnnotation.typeArguments().size() == 2
            assert genericType.name == Map.name
            assert boundGenericTypes.size() == 2
            assert boundGenericTypes[0].name == String.name
            assert boundGenericTypes[1].name == Object.name
            return element
        }
    }

    def "test method parameter bytes type resolves to byte array"() {
        given:
        def pythonCode = '''
class TypeTestService:
    def save(self, data: bytes) -> object:
        pass
'''

        expect:
        buildClassElement(pythonCode, "TypeTestService") { ClassElement element ->
            def method = element.findMethod("save").get()
            def parameter = method.parameters[0]

            assert parameter.type.name == "byte"
            assert parameter.type.array
            assert parameter.type.arrayDimensions == 1
            assert parameter.genericType.name == "byte"
            assert parameter.genericType.array
            assert parameter.genericType.arrayDimensions == 1
            return element
        }
    }

    def "test primitive method return and parameter types"() {
        given:
        def pythonCode = '''
class PrimitiveService:
    def calculate(self, count: int, enabled: bool, ratio: float) -> int:
        return count
'''

        expect:
        buildClassElement(pythonCode, "PrimitiveService") { ClassElement element ->
            def method = element.findMethod("calculate").get()

            assert method.returnType.name == "int"
            assert method.returnType.canonicalName == "int"
            assert method.returnType.primitive
            assert method.returnType.nonNull
            assert !method.returnType.nullable
            assert method.genericReturnType.nonNull
            assert !method.genericReturnType.nullable

            assert method.parameters*.type*.name == ["int", "boolean", "double"]
            assert method.parameters*.type*.canonicalName == ["int", "boolean", "double"]
            assert method.parameters.every { it.type.primitive }
            assert method.parameters.every { it.type.nonNull }
            assert method.parameters.every { !it.type.nullable }
            assert method.parameters.every { it.genericType.nonNull }
            assert method.parameters.every { !it.genericType.nullable }
            return element
        }
    }

    def "test primitive method types compare with primitive elements"() {
        given:
        def pythonCode = '''
class PrimitiveComparison:
    def enabled(self, count: int) -> bool:
        return count > 0
'''

        expect:
        buildClassElement(pythonCode, "PrimitiveComparison") { ClassElement element ->
            def method = element.findMethod("enabled").get()
            def parameterType = method.parameters[0].type
            def returnType = method.returnType

            assert element != PrimitiveElement.BOOLEAN
            assert element != PrimitiveElement.VOID
            assert element != PrimitiveElement.BOOLEAN.withArrayDimensions(4)
            assert PrimitiveElement.VOID != element
            assert PrimitiveElement.INT != element
            assert PrimitiveElement.INT.withArrayDimensions(2) != element
            assert parameterType == PrimitiveElement.INT
            assert PrimitiveElement.INT == parameterType
            assert returnType == PrimitiveElement.BOOLEAN
            assert PrimitiveElement.BOOLEAN == returnType
            return element
        }
    }

    def "test method element query filters declared abstract and concrete methods"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod

class Parent(ABC):
    def parent_method(self) -> bool:
        return True

    @abstractmethod
    def inherited_abstract(self) -> bool:
        pass

class QueryService(Parent):
    def declared_method(self) -> bool:
        return True

    @abstractmethod
    def declared_abstract(self) -> bool:
        pass
'''

        expect:
        buildClassElement(pythonCode, "QueryService") { ClassElement element ->
            def allMethods = element.getEnclosedElements(ElementQuery.ALL_METHODS)
            def declared = element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
            def abstractMethods = element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyAbstract())
            def concrete = element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyConcrete())

            assert allMethods*.name as Set == ["declared_method", "declared_abstract", "parent_method", "inherited_abstract"] as Set
            assert declared*.name as Set == ["declared_method", "declared_abstract"] as Set
            assert abstractMethods*.name as Set == ["declared_abstract", "inherited_abstract"] as Set
            assert concrete*.name as Set == ["declared_method", "parent_method"] as Set
            return element
        }
    }

    def "test constructor element query returns declared and inherited constructors"() {
        given:
        def pythonCode = '''
class Parent:
    def __init__(self, name: str):
        self.name = name

class ConstructorService(Parent):
    def __init__(self, name: str, count: int):
        super().__init__(name)
        self.count = count
'''

        expect:
        buildClassElement(pythonCode, "ConstructorService") { ClassElement element ->
            def declaredConstructors = element.getEnclosedElements(ElementQuery.CONSTRUCTORS)
            def allConstructors = element.getEnclosedElements(ElementQuery.of(ConstructorElement))

            assert declaredConstructors.size() == 1
            assert declaredConstructors.first().declaringType.name == "python.ConstructorService"
            assert declaredConstructors.first().parameters*.name == ["name", "count"]
            assert declaredConstructors.first().parameters*.type*.name == [String.name, "int"]

            assert allConstructors.size() == 2
            assert allConstructors*.declaringType*.name as Set == ["python.Parent", "python.ConstructorService"] as Set
            return element
        }
    }

    def "test override method inherits annotations from python base method"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
import java
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Post

Publisher = java.type("org.reactivestreams.Publisher")

class Pet:
    pass

class PetOperations(ABC):
    @Post
    @SingleResult
    @abstractmethod
    def save(self, name: str) -> Publisher[Pet]:
        pass

class PetClient(PetOperations):
    @SingleResult
    @abstractmethod
    def save(self, name: str) -> Publisher[Pet]:
        pass
'''

        expect:
        buildClassElement(pythonCode, "PetClient") { ClassElement element ->
            def method = element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
                .find { it.name == "save" }

            assert method.annotationMetadata.hasAnnotation("io.micronaut.core.async.annotation.SingleResult")
            assert method.annotationMetadata.hasAnnotation("io.micronaut.http.annotation.Post")
            assert method.annotationMetadata.hasStereotype("io.micronaut.http.annotation.HttpMethodMapping")
            assert method.methodAnnotationMetadata.hasAnnotation("io.micronaut.core.async.annotation.SingleResult")
            assert method.methodAnnotationMetadata.hasAnnotation("io.micronaut.http.annotation.Post")
            return element
        }
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0035")
    def "test overridden method reports python base method"() {
        given:
        def pythonCode = '''
from typing import Annotated
from jakarta.validation.constraints import NotBlank

class Repository:
    def save(self, name: Annotated[str, NotBlank]) -> str:
        return name

class DefaultRepository(Repository):
    def save(self, name: str) -> str:
        return name
'''

        expect:
        buildClassElement(pythonCode, "DefaultRepository") { ClassElement element ->
            MethodElement method = element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
                .find { it.name == "save" }
            def overridden = method.overriddenMethods

            assert overridden.size() == 1
            assert overridden.first().declaringType.name == "python.Repository"
            assert overridden.first().parameters[0].hasAnnotation("jakarta.validation.constraints.NotBlank")
            return element
        }
    }

    def "test client override with class header keeps client interceptor binding"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
import java
from micronaut.core.async_.annotation import SingleResult
from micronaut.http.annotation import Header, Post
from micronaut.http.client.annotation import Client

Publisher = java.type("org.reactivestreams.Publisher")

class Pet:
    pass

class PetOperations(ABC):
    @Post
    @SingleResult
    @abstractmethod
    def save(self, name: str) -> Publisher[Pet]:
        pass

@Client("/pets")
@Header(name="X-Pet-Client", value="${pet.client.id}")
class PetClient(PetOperations):
    @SingleResult
    @abstractmethod
    def save(self, name: str) -> Publisher[Pet]:
        pass
'''

        expect:
        buildClassElement(pythonCode, "PetClient") { ClassElement element ->
            def saves = element.getEnclosedElements(ElementQuery.ALL_METHODS)
                .findAll { it.name == "save" }
            def method = saves.find { it.declaringType.name == "PetClient" } ?: saves.first()

            assert saves.size() == 1
            assert method.declaringType.name == "python.PetClient"
            assert method.annotationMetadata.hasAnnotation("io.micronaut.http.client.annotation.Client")
            assert method.annotationMetadata.hasAnnotation("io.micronaut.http.annotation.Headers")
            assert method.annotationMetadata.hasAnnotation("io.micronaut.http.annotation.Post")
            assert method.annotationMetadata.hasStereotype("io.micronaut.aop.Introduction")
            assert method.annotationMetadata.getAnnotationValuesByType(InterceptorBinding)*.stringValue()*.orElse(null)
                .contains("io.micronaut.http.client.annotation.Client")
            return element
        }
        def definition = buildBeanDefinition("python", "PetClient\$RuntimeProxy", pythonCode)
        definition.executableMethods
            .find { it.methodName == "save" }
            .annotationMetadata
            .getAnnotationValuesByType(InterceptorBinding)*.stringValue()*.orElse(null)
            .contains("io.micronaut.http.client.annotation.Client")
        definition.executableMethods
            .find { it.methodName == "save" }
            .annotationMetadata
            .getAnnotationValuesByName("io.micronaut.aop.InterceptorBinding")*.stringValue()*.orElse(null)
            .contains("io.micronaut.http.client.annotation.Client")
        def executableMethods = definition.executableMethods.toArray(new io.micronaut.inject.ExecutableMethod[0])
        new AnnotationMetadataHierarchy(executableMethods)
            .getAnnotationValuesByName("io.micronaut.aop.InterceptorBinding")*.stringValue()*.orElse(null)
            .contains("io.micronaut.http.client.annotation.Client")
        Qualifiers.byInterceptorBinding(new AnnotationMetadataHierarchy(executableMethods)).toString()
            .contains("Client")

        def context = ApplicationContext.builder(["pet.client.id": "11"]).build()
        try {
            definition.configure(context.environment)
            def configuredExecutableMethods = definition.executableMethods.toArray(new io.micronaut.inject.ExecutableMethod[0])
            assert configuredExecutableMethods
                .collectMany { InterceptedMethodUtil.resolveInterceptorBinding(it.annotationMetadata, InterceptorKind.INTRODUCTION).toList() }
                *.stringValue()*.orElse(null)
                .contains("io.micronaut.http.client.annotation.Client")
        } finally {
            context.close()
        }
    }

    def "test annotation expression values are converted to evaluated expression references"() {
        given:
        def pythonCode = '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from micronaut.context.annotation import Mapper
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Product:
    price: float

@Introspected
@dataclass
class ProductDTO:
    price: str

class ProductMappers(ABC):
    @Mapper.Mapping(to="price", **{"from": "#{product.price * 2}", "format": "$#.00"})
    @abstractmethod
    def to_product_dto(self, product: Product) -> ProductDTO:
        pass
'''

        expect:
        buildClassElement(pythonCode, "ProductMappers") { ClassElement element ->
            def method = element.findMethod("to_product_dto").get()
            def mappings = method.annotationMetadata.getAnnotationValuesByType(Mapper.Mapping)
            def fromValue = mappings[0].values["from"]

            assert fromValue instanceof EvaluatedExpressionReference
            assert fromValue.annotationValue == "#{product.price * 2}"
            assert method.annotationMetadata.hasEvaluatedExpressions()
            return element
        }
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
        "Optional present"    | "Optional[str]"      | '"Alice"'   | Optional.of("Alice") | Optional.class  | Optional.class
        "Optional empty"      | "Optional[str]"      | "None"      | Optional.empty()     | Optional.class  | Optional.class
        "None return type"    | "None"               | "None"      | null                 | null            | void.class
        "Dict return type"    | "dict[str, int]"     | '{"a": 1}'  | ["a": 1]             | HashMap.class   | Map.class
        "List return type"    | "list[int]"          | "[1, 2, 3]" | [1, 2, 3]            | ArrayList.class | List.class
        "String return type"  | "str"                | '"hello"'   | "hello"              | String.class    | String.class
        "Integer return type" | "int"                | "42"        | 42                   | Integer         | Integer.TYPE
        "Boolean True"        | "bool"               | "True"      | true                 | Boolean         | Boolean.TYPE
        "Boolean False"       | "bool"               | "False"     | false                | Boolean         | Boolean.TYPE
        "Float return type"   | "float"              | "3.14"      | 3.14d                | Double          | Double.TYPE

    }

    @Unroll
    def "test different return types via generics: #description"() {
        given: "Python code with specific return type annotation"
        def pythonCode = """
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from typing import Generic, TypeVar, List, Optional

T = TypeVar('T')

class MyBase(Generic[T]):
    @Executable
    def get_value(self) -> T:
        return $pythonValue

@Singleton
class TypeTestService(MyBase[$pythonTypeAnnotation]):
    pass
"""

        when: "Building ApplicationContext and calling method"
        def context = buildContext(pythonCode)
        def result = getBean(context, "python.TypeTestService").get_value()
        def beanDefinition = getBeanDefinition(context, "python.TypeTestService")

        then: "Result should be correctly converted to expected type"
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].returnType.asArgument() == returnType
        result == expectedValue

        cleanup: "Ensure context is properly closed"
        context?.close()

        where:
        description           | pythonTypeAnnotation | pythonValue | expectedValue | expectedType    | returnType
        "Optional present"    | "Optional[str]"      | '"Alice"'   | "Alice"       | Optional.class  | Argument.of(Optional, String)
        "Optional empty"      | "Optional[str]"      | "None"      | null          | Optional.class  | Argument.of(Optional, String)
        "Dict return type"    | "dict[str, int]"     | '{"a": 1}'  | ["a": 1]      | HashMap.class   | Argument.mapOf(String, Integer)
        "List return type"    | "list[int]"          | "[1, 2, 3]" | [1, 2, 3]     | ArrayList.class | Argument.listOf(Integer)
        "String return type"  | "str"                | '"hello"'   | "hello"       | String.class    | Argument.STRING
        "Integer return type" | "int"                | "42"        | 42            | Integer         | Argument.INT
        "Boolean True"        | "bool"               | "True"      | true          | Boolean         | Argument.BOOLEAN
        "Boolean False"       | "bool"               | "False"     | false         | Boolean         | Argument.BOOLEAN
        "Float return type"   | "float"              | "3.14"      | 3.14d         | Double          | Argument.DOUBLE

    }

    def "test __qualname__ attribute resolution in decorator parameters"() {
        given: "Python code with decorator using __qualname__"
        def pythonCode = '''
from micronaut.core.annotation import TypeHint

@TypeHint(value=TestClass.__qualname__)
class TestClass:
    pass
'''

        expect: "The __qualname__ should resolve to the qualified class name"
        buildClassElement(pythonCode) { ClassElement element ->
            assert element != null
            assert element.getSimpleName() == "TestClass"
            // The annotation value should be the qualified name
            assert element.stringValue("io.micronaut.core.annotation.TypeHint", "value").get() == "python.TestClass"
            return element
        }
    }

}
