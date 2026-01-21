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

import io.micronaut.inject.BeanDefinition

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
}
