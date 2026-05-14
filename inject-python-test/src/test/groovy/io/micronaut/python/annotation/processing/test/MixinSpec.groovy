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

import io.micronaut.context.annotation.Property
import io.micronaut.core.beans.BeanIntrospection
import spock.lang.PendingFeature

class MixinSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0081")
    void "test mixin adds introspection and annotation metadata to target class"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection("python.MyBean", '''
import java
from typing import Annotated
from micronaut.context.annotation import Executable, Mixin, Property
from micronaut.core.annotation import Introspected

Object = java.type("java.lang.Object")

class MyBean:
    name: str = None

    def describe(self, value: str) -> str:
        return value

@Mixin(value=Object, target="python.MyBean", includeAnnotations=["io.micronaut"])
@Introspected
class MyBeanMixin:
    name: Annotated[str, Property(name="mixed.name", value="property")] = None

    @Executable
    @Property(name="mixed.method", value="method")
    def describe(self, value: Annotated[str, Property(name="mixed.argument", value="argument")]) -> str:
        return value
''')

        then:
        introspection != null
        introspection.getProperty("name").get().annotationMetadata.stringValue(Property).get() == "property"

        when:
        def method = introspection.beanMethods.iterator().next()

        then:
        method.annotationMetadata.stringValue(Property).get() == "method"
        method.arguments[0].annotationMetadata.stringValue(Property).get() == "argument"
    }
}
