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

import io.micronaut.context.annotation.DefaultImplementation

class DefaultImplementationSpec extends AbstractPythonTypeElementSpec {

    void "test pick default implementation when multiple candidates"() {
        given:
        def context = buildContext('''\
from abc import ABC
from jakarta.inject import Singleton
from micronaut.context.annotation import DefaultImplementation, Executable

@DefaultImplementation(name="python.TestImpl")
class Test(ABC):
    def name(self) -> str:
        return "base"

@Singleton
class TestImpl(Test):
    @Executable
    def name(self) -> str:
        return "default"

@Singleton
class TestImpl2(Test):
    @Executable
    def name(self) -> str:
        return "secondary"
''')
        def testClass = context.classLoader.loadClass("python.Test")
        def implDefinition = getBeanDefinition(context, "python.TestImpl")
        def impl2Definition = getBeanDefinition(context, "python.TestImpl2")

        expect:
        implDefinition.getDefaultImplementation().name == "python.TestImpl"
        impl2Definition.getDefaultImplementation().name == "python.TestImpl"
        !implDefinition.hasDeclaredAnnotation(DefaultImplementation)
        !impl2Definition.hasDeclaredAnnotation(DefaultImplementation)
        context.getBean(testClass).name() == "default"

        cleanup:
        context.close()
    }
}
