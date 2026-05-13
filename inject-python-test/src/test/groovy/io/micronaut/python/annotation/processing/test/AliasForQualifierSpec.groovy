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

import io.micronaut.core.annotation.AnnotationUtil
import spock.lang.PendingFeature

import java.util.function.Function

class AliasForQualifierSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0022")
    void "test alias for named qualifier stereotypes"() {
        given:
        def context = buildContext('''\
from typing import Annotated

from jakarta.inject import Named, Singleton
from micronaut.context.annotation import AliasFor, Executable, Factory
from java.util.function import Function

@Singleton
@Executable
def TestAnnotation(
    value: Annotated[str, AliasFor(annotation=Named, member="value")] = "",
):
    def decorator(func):
        return func
    return decorator

class EchoFunction(Function[str, int]):
    def apply(self, value: str) -> int:
        return 10

@Factory
class TestFactory:

    @TestAnnotation("foo")
    def my_func(self) -> Function[str, int]:
        return EchoFunction()
''')

        when:
        def definition = context.getBeanDefinition(Function)

        then:
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == AnnotationUtil.NAMED
        definition.getValue(AnnotationUtil.NAMED, String).get() == "foo"

        cleanup:
        context.close()
    }
}
