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
package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.context.annotation.DefaultImplementation
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Requirements
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

class AnnotationInheritanceSpec extends AbstractPythonTypeElementSpec {

    void "test declared scopes qualifiers and requirements on types"() {
        given:
        def definition = buildBeanDefinition("python", "Test", '''
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Requires

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Test:
    pass
''')

        expect:
        definition.hasDeclaredAnnotation(AnnotationUtil.SINGLETON)
        definition.hasDeclaredAnnotation(AnnotationUtil.NAMED)
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
        definition.hasAnnotation(AnnotationUtil.SINGLETON)
        definition.hasAnnotation(AnnotationUtil.NAMED)
        definition.hasStereotype(AnnotationUtil.SCOPE)
        definition.hasStereotype(AnnotationUtil.QUALIFIER)
        definition.isSingleton()
        definition.scopeName.isPresent()
        definition.scopeName.get() == AnnotationUtil.SINGLETON
        definition.declaredQualifier == Qualifiers.byName("test")
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 1
    }

    void "test inherited scopes qualifiers and requirements on non bean type are not treated as declared"() {
        given:
        def definition = buildBeanDefinition("python", "Test", '''
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Requires

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Parent:
    pass

class Test(Parent):
    pass
''')

        expect:
        definition == null
    }

    void "test inherited scopes qualifiers and requirements on bean type are not inherited"() {
        given:
        def definition = buildBeanDefinition("python", "Test", '''
from jakarta.inject import Named, Singleton
from micronaut.context.annotation import Prototype, Requires

@Singleton
@Named("test")
@Requires(property="foo.bar")
class Parent:
    pass

@Prototype
class Test(Parent):
    pass
''')

        expect:
        definition.hasDeclaredAnnotation(Prototype)
        definition.declaredAnnotationNames == [Prototype.name] as Set
        definition.hasDeclaredStereotype(AnnotationUtil.SCOPE)
        !definition.hasDeclaredAnnotation(AnnotationUtil.NAMED)
        !definition.hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
        !definition.hasAnnotation(AnnotationUtil.NAMED)
        !definition.hasAnnotation(AnnotationUtil.QUALIFIER)
        definition.scopeName.isPresent()
        definition.scopeName.get() == Prototype.name
        !definition.isSingleton()
        definition.declaredQualifier == null
        !definition.hasAnnotation(Requirements)
        definition.getDeclaredAnnotationValuesByType(Requires).size() == 0
    }

    void "test inherited default implementation metadata is not treated as declared"() {
        given:
        def definition = buildBeanDefinition("python", "TestImpl", '''
from abc import ABC
from micronaut.context.annotation import DefaultImplementation, Prototype

@DefaultImplementation(name="python.TestImpl")
class Test(ABC):
    pass

@Prototype
class TestImpl(Test):
    pass
''')

        expect:
        definition.hasAnnotation(DefaultImplementation)
        !definition.hasDeclaredAnnotation(DefaultImplementation)
        definition.getDefaultImplementation().name == "python.TestImpl"
    }
}
