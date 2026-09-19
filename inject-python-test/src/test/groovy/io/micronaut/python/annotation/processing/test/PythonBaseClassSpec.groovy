/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.ApplicationContext
import io.micronaut.python.compiler.PyronautCompiler
import org.junit.jupiter.api.Test

/**
 * Python classes extending Python classes: the generated Java class extends the generated class of
 * the base, for beans and for JUnit test classes.
 */
class PythonBaseClassSpec extends AbstractPythonTypeElementSpec {

    void "a plain Python base class is a Java supertype of the generated class"() {
        given:
        ApplicationContext ctx = buildContext('''
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable


class Processor:
    def __init__(self):
        self.prefix = "base"

    def process(self, value: str) -> str:
        return self.prefix + ":" + self.transform(value)

    def transform(self, value: str) -> str:
        return value


@Singleton
class UpperProcessor(Processor):
    def __init__(self):
        super().__init__()
        self.prefix = "upper"

    @Executable
    def process(self, value: str) -> str:
        return super().process(value)

    @Executable
    def transform(self, value: str) -> str:
        return value.upper()
''', true)

        when:
        Class<?> baseClass = ctx.classLoader.loadClass('python.Processor')
        Class<?> generatedClass = ctx.classLoader.loadClass('python.UpperProcessor')
        def processor = ctx.getBean(baseClass)

        then:
        baseClass.isAssignableFrom(generatedClass)
        generatedClass.isInstance(processor)
        processor.process('abc') == 'upper:ABC'
        processor.transform('abc') == 'ABC'

        cleanup:
        ctx?.close()
    }

    void "a JUnit test class can extend a Python base class"() {
        given:
        def pythonCode = '''
from org.junit.jupiter.api import Test


class BaseSpec:
    def setup_value(self) -> str:
        return "value"

    @Test
    def base_test(self) -> None:
        assert self.setup_value() == "value"


class DerivedSpec(BaseSpec):
    @Test
    def derived_test(self) -> None:
        assert self.setup_value() == "value"
'''
        def tempDir = File.createTempDir("python-test-base", "")
        def compiler = PyronautCompiler.builder()
            .pythonCode(pythonCode)
            .targetDir(tempDir)
            .build()

        when:
        compiler.compile()
        def classLoader = compiler.buildClassLoader()
        Class<?> derived = classLoader.loadClass('python.DerivedSpec')
        Class<?> base = classLoader.loadClass('python.BaseSpec')

        then: 'the test class stands alone (its Python object is created lazily) and carries the inherited test'
        !base.isAssignableFrom(derived)
        derived.getDeclaredMethod('derived_test').getAnnotation(Test) != null
        derived.getDeclaredMethod('base_test').getAnnotation(Test) != null
        derived.getDeclaredMethod('setup_value') != null
        base.getDeclaredMethod('base_test') != null

        cleanup:
        tempDir.deleteDir()
    }
}
