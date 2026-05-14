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
package io.micronaut.python.annotation.processing.test.annotate

import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec
import io.micronaut.python.annotation.processing.test.annotate.stereotype.VisitorMarker
import io.micronaut.python.annotation.processing.test.annotate.stereotype.VisitorQualifier
import io.micronaut.python.annotation.processing.test.annotate.stereotype.VisitorScope
import jakarta.inject.Qualifier
import jakarta.inject.Scope
import spock.lang.PendingFeature

class StereotypeVisitorSpec extends AbstractPythonTypeElementSpec {

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0077")
    void "test visitor added scope stereotype creates bean"() {
        when:
        def definition = buildBeanDefinition("python", "StereotypeTest", """
import java

VisitorMarker = java.type("${VisitorMarker.name}")

@VisitorMarker()
class StereotypeTest:
    pass
""")

        then:
        definition != null
        definition.getAnnotationNameByStereotype(AnnotationUtil.SCOPE).get() == VisitorScope.name
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0078")
    void "test visitor added qualifier stereotype resolves constructor injection"() {
        given:
        def definition = buildBeanDefinition("python", "TestBean", """
from typing import Annotated
from jakarta.inject import Singleton
import java

VisitorQualifier = java.type("${VisitorQualifier.name}")

@Singleton
class Other:
    pass

@Singleton
class TestBean:
    def __init__(self, other: Annotated[Other, VisitorQualifier()]):
        self.other = other
""")

        expect:
        definition.constructor.arguments[0]
            .annotationMetadata
            .hasDeclaredStereotype(AnnotationUtil.QUALIFIER)
    }

    static class StereotypeAddingVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void start(VisitorContext visitorContext) {
            visitorContext.getClassElement(VisitorScope).ifPresent { ClassElement element ->
                element.annotate(Scope)
            }
            visitorContext.getClassElement(VisitorMarker).ifPresent { ClassElement element ->
                element.annotate(VisitorScope)
            }
            visitorContext.getClassElement(VisitorQualifier).ifPresent { ClassElement element ->
                element.annotate(Qualifier)
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }
}
