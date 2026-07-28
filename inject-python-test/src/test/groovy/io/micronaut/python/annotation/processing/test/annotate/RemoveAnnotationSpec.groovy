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

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import jakarta.inject.Singleton

class RemoveAnnotationSpec extends AbstractPythonTypeElementSpec {

    void "test replacing scope annotation from visitor"() {
        when:
        def definition = buildBeanDefinition("python", "ReplaceScope", '''
from jakarta.inject import Singleton

@Singleton
class ReplaceScope:
    pass
''')

        then:
        definition
        definition.hasStereotype(AnnotationUtil.SCOPE)
        definition.hasDeclaredAnnotation(Prototype)
        !definition.hasDeclaredAnnotation(Singleton)
        def stereotypes = definition.getAnnotationNamesByStereotype(AnnotationUtil.SCOPE)
        stereotypes.contains(Prototype.name)
        stereotypes.size() == 1
    }

    void "test removing only scope annotation removes bean definition"() {
        expect:
        buildBeanDefinition("python", "RemoveOnlyScope", '''
from jakarta.inject import Singleton

@Singleton
class RemoveOnlyScope:
    pass
''') == null
    }

    void "test removing repeatable annotation type removes container metadata"() {
        when:
        def definition = buildBeanDefinition("python", "RemoveRepeatableProperties", '''
from jakarta.inject import Singleton
from micronaut.context.annotation import Property

@Property(name="one", value="1")
@Property(name="two", value="2")
@Singleton
class RemoveRepeatableProperties:
    pass
''')

        then:
        definition
        !definition.hasDeclaredAnnotation(PropertySource)
        !definition.hasDeclaredAnnotation(Property)
        definition.getAnnotationValuesByType(Property).empty
    }

    static class RemoveAnnotationVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            switch (element.simpleName) {
                case "ReplaceScope":
                    element.removeAnnotation(Singleton.name)
                    element.annotate(Prototype)
                    break
                case "RemoveOnlyScope":
                    element.removeAnnotation(Singleton.name)
                    break
                case "RemoveRepeatableProperties":
                    element.removeAnnotation(Property.name)
                    break
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
