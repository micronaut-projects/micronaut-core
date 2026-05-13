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

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import jakarta.validation.constraints.Size

/**
 * Tests for Python @Executable annotation producing ExecutableMethod instances.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class ExecutableMethodSpec extends AbstractPythonTypeElementSpec {

    def "test @Executable produces BeanDefinition with ExecutableMethod"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class ExecutableService:
    @Executable
    def execute_task(self, task_name: str) -> str:
        return f"Executed: {task_name}"
'''

        when:
        def context = buildContext(pythonCode)
        def beanDefinition = getBeanDefinition(context, "python.ExecutableService")

        then:
        beanDefinition != null
        beanDefinition.getExecutableMethods().size() == 1

        def executableMethod = beanDefinition.getExecutableMethods().first()
        executableMethod != null
        executableMethod.getMethodName() == "execute_task"

        cleanup:
        context?.close()
    }

    def "test executable method exposes return and argument types"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
@Executable
class Calculator:
    def round_value(self, num: float) -> int:
        return round(num)
'''

        when:
        def context = buildContext(pythonCode)
        def beanDefinition = getBeanDefinition(context, "python.Calculator")
        def bean = getBean(context, "python.Calculator")
        def executableMethod = beanDefinition.executableMethods.find { it.methodName == "round_value" }

        then:
        executableMethod != null
        executableMethod.arguments[0].type == Double.TYPE
        executableMethod.returnType.type == Integer.TYPE
        bean.round_value(1.6d) == 2

        cleanup:
        context?.close()
    }

    def "test executable bytes argument retains validation metadata"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from jakarta.validation.constraints import Size
from micronaut.context.annotation import Executable
from typing import Annotated

@Singleton
class BlobService:
    @Executable
    def save(self, data: Annotated[bytes, Size(max=1024)]) -> None:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def beanDefinition = getBeanDefinition(context, "python.BlobService")
        def executableMethod = beanDefinition.findMethod("save", byte[].class).get()

        then:
        executableMethod.arguments[0].type == byte[].class
        executableMethod.arguments[0].annotationMetadata.hasDeclaredAnnotation(Size)
        executableMethod.arguments[0].annotationMetadata.intValue(Size, "max").getAsInt() == 1024

        cleanup:
        context?.close()
    }

    def "test executable method alone does not create bean definition"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "Utility", '''
from micronaut.context.annotation import Executable

class Utility:
    @Executable
    def round_value(self, num: float) -> int:
        return round(num)
''')

        then:
        definition == null
    }

    def "test executable method metadata includes bean scope"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class MetadataService:
    @Executable
    def some_method(self) -> None:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def beanDefinition = getBeanDefinition(context, "python.MetadataService")
        def method = beanDefinition.findMethod("some_method").get()

        then:
        beanDefinition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        method.annotationMetadata.hasAnnotation(AnnotationUtil.SINGLETON)
        method.annotationMetadata.hasDeclaredAnnotation(Executable)

        cleanup:
        context?.close()
    }

    def "test class-level @Executable produces executable methods"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from typing import Annotated
from jakarta.inject import Named

@Singleton
@Executable
class ExecutableService:
    def method_one(self, one: Annotated[str, Named("foo")]) -> str:
        return "good"

    def method_two(self, one: str, two: str) -> str:
        return "good"

    def method_zero(self) -> str:
        return "good"
'''

        when:
        def context = buildContext(pythonCode)
        def beanDefinition = getBeanDefinition(context, "python.ExecutableService")

        then:
        beanDefinition != null
        beanDefinition.executableMethods*.methodName == ["method_one", "method_two", "method_zero"]
        beanDefinition.executableMethods[0].arguments[0].annotationMetadata.stringValue(AnnotationUtil.NAMED).get() == "foo"

        cleanup:
        context?.close()
    }

    def "test executable methods can require startup processing"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

@Singleton
class ExecutableService:
    def simple(self) -> None:
        pass

    @Executable
    def abc(self) -> None:
        pass

    @Executable(processOnStartup=True)
    def foo(self) -> None:
        pass

    @Executable(processOnStartup=True)
    def bar(self) -> None:
        pass

    @Executable
    def some(self) -> None:
        pass
'''

        when:
        def context = buildContext(pythonCode)
        def beanDefinition = getBeanDefinition(context, "python.ExecutableService")

        then:
        beanDefinition.executableMethods*.methodName == ["abc", "foo", "bar", "some"]
        beanDefinition.requiresMethodProcessing()
        beanDefinition.executableMethodsForProcessing*.methodName == ["foo", "bar"]

        cleanup:
        context?.close()
    }

    def "test @Executable can be used as stereotype of another annotation"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.python.compiler import MyExecutable
from typing import Annotated

@Singleton
class StereotypeService:
    @MyExecutable
    def custom_executable_method(self, data: str) -> str:
        return f"Processed: {data}"
'''

        when:
        def context = buildContext(pythonCode)
        def beanDefinition = getBeanDefinition(context, "python.StereotypeService")
        def bean = getBean(context, "python.StereotypeService")

        then:
        beanDefinition != null
        beanDefinition.getExecutableMethods().size() == 1

        def executableMethod = beanDefinition.getExecutableMethods().first()
        executableMethod != null
        executableMethod.getMethodName() == "custom_executable_method"
        bean.custom_executable_method("test") == 'Processed: test'

        cleanup:
        context?.close()
    }

    def "method returning None maps to Java null for Python class return"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

class Book:
    def __init__(self, title: str):
        self.title = title

@Singleton
class Service:
    @Executable
    def show(self, title: str) -> Book:
        return None
'''

        when:
        def context = buildContext(pythonCode)
        def bean = getBean(context, "python.Service")

        then:
        bean != null
        bean.show("a title") == null

        cleanup:
        context?.close()
    }

    def "test abstract base with executable method is not a bean"() {
        when:
        BeanDefinition definition = buildBeanDefinition("python", "GenericController", '''
from abc import ABC, abstractmethod
from typing import Generic, TypeVar
from micronaut.context.annotation import Executable

T = TypeVar("T")

class GenericController(Generic[T], ABC):
    @abstractmethod
    def get_path(self) -> str:
        pass

    @Executable
    def save(self, entity: T) -> str:
        return "parent"
''')

        then:
        definition == null
    }

    def "test inherited executable methods resolve generic arguments"() {
        given:
        BeanDefinition definition = buildBeanDefinition("python", "StatusController", '''
from typing import Generic, TypeVar
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable

T = TypeVar("T")
ID = TypeVar("ID")

class GenericController(Generic[T, ID]):
    @Executable
    def save(self, entity: T) -> T:
        return entity

    @Executable
    def find(self, id: ID) -> T:
        return None

    def create(self, id: ID) -> T:
        raise NotImplementedError()

@Singleton
@Executable
class StatusController(GenericController[str, int]):
    def create(self, id: int) -> str:
        return str(id)
''')

        expect:
        definition != null
        definition.executableMethods.any { it.methodName == "create" && it.argumentTypes == [Integer.TYPE] as Class[] }
        definition.executableMethods.any { it.methodName == "save" && it.argumentTypes == [String] as Class[] }
        definition.executableMethods.any { it.methodName == "find" && it.argumentTypes == [Integer.TYPE] as Class[] }
        definition.executableMethods.size() == 3
    }
}
