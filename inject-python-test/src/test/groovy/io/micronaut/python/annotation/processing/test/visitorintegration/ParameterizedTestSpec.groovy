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
package io.micronaut.python.annotation.processing.test.visitorintegration

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.platform.commons.support.AnnotationSupport

import java.lang.reflect.InvocationTargetException

/**
 * JUnit parameterized tests ({@code org.junit.jupiter.params}) can be written in Python: the annotations are
 * importable at run time, and a test template method is bridged like a {@code @Test} method with its
 * annotations copied onto the generated Java method.
 */
class ParameterizedTestSpec extends AbstractPythonTypeElementSpec {

    void "test a parameterized test method is generated with its parameter source"() {
        given:
        def context = buildContext('''
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from org.junit.jupiter.params import ParameterizedTest
from org.junit.jupiter.params.provider import ValueSource


@MicronautTest(startApplication=False)
class SchemaGenerationTest:

    @ParameterizedTest
    @ValueSource(strings=["llama", "alpaca"])
    def test_build_json_schema(self, name: str) -> None:
        assert name in ("llama", "alpaca"), f"unexpected schema {name}"

    @Test
    def test_plain(self) -> None:
        assert True
''')
        def type = context.classLoader.loadClass("python.SchemaGenerationTest")
        def parameterized = type.getMethod("test_build_json_schema", String)
        def plain = type.getMethod("test_plain")

        expect:
        type.getAnnotation(MicronautTest) != null
        !type.getAnnotation(MicronautTest).startApplication()
        parameterized.getAnnotation(ParameterizedTest) != null
        parameterized.getAnnotation(Test) == null
        AnnotationSupport.findRepeatableAnnotations(parameterized, ValueSource)*.strings() == [["llama", "alpaca"] as String[]]
        plain.getAnnotation(Test) != null
        plain.getAnnotation(ParameterizedTest) == null

        when: "the generated test methods are invoked the way the JUnit engine does"
        def instance = type.getConstructor().newInstance()
        parameterized.invoke(instance, "llama")
        parameterized.invoke(instance, "alpaca")
        plain.invoke(instance)

        then:
        noExceptionThrown()

        when:
        parameterized.invoke(instance, "goat")

        then:
        def e = thrown(InvocationTargetException)
        e.cause.message.contains("unexpected schema goat")

        cleanup:
        context?.close()
    }
}
