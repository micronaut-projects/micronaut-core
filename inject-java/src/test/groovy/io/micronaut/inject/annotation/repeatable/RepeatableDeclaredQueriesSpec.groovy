/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.annotation.repeatable

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery

import java.lang.annotation.Annotation

/**
 * A repeatable annotation is stored under its container ({@code Qs}) while the stereotype
 * index names the member ({@code Q}). The declared queries by name must resolve the member
 * through its container exactly like the {@code Class} overloads already do.
 */
class RepeatableDeclaredQueriesSpec extends AbstractTypeElementSpec {

    private static final String Q = 'repeatabledeclared.Q'
    private static final String QS = 'repeatabledeclared.Qs'

    private static final String SOURCE = '''
package repeatabledeclared;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import io.micronaut.context.annotation.Executable;
import jakarta.inject.Inject;
import jakarta.inject.Qualifier;
import jakarta.inject.Singleton;

@Singleton
@Q("a")
@Q("b")
class Rep {

    @Inject
    @Q("a")
    @Q("b")
    Runnable field;

    Rep(@Q("a") @Q("b") Runnable ctorParam) {
    }

    @Executable
    void method(@Q("a") @Q("b") Runnable methodParam) {
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Qualifier
@Repeatable(Qs.class)
@interface Q {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@interface Qs {
    Q[] value();
}
'''

    /**
     * What every by-name query answers on an element carrying {@code @Q("a") @Q("b")}. Only
     * {@code getDeclaredAnnotationNamesByStereotype} changes: it named nothing before, because the stereotype
     * index lists the member while the declared annotations hold the container. The {@code has*} queries by
     * name deliberately keep answering for the name as written — the compiler relies on "declared under its own
     * name" to mean explicitly written (a Kotlin data-class configuration property is one that does not carry
     * {@code @Property} itself), so a repeatable stored in its container is reached through the {@code Class}
     * overloads, which map to the container, and through the container's own name.
     */
    private static final Map<String, Object> EXPECTED = [
            hasDeclaredAnnotationQs        : true,
            hasAnnotationQs                : true,
            annotationNamesByStereotype    : [Q],
            declaredAnnotationValuesByNameQ: ['a', 'b'],
            annotationValuesByNameQ        : ['a', 'b'],
            declaredAnnotationNamesByStereotype: [Q],
            hasDeclaredAnnotationQ         : false,
            hasDeclaredStereotypeQ         : false,
            hasAnnotationQ                 : false,
            hasStereotypeQ                 : false,
    ]

    void "test declared queries by name see a repeatable annotation at compile time"() {
        given:
        ClassElement element = buildClassElement(SOURCE)
        def field = element.getEnclosedElement(ElementQuery.ALL_FIELDS.named('field')).get()
        def ctorParam = element.getPrimaryConstructor().get().getParameters()[0]
        def methodParam = element.getEnclosedElement(ElementQuery.ALL_METHODS.named('method')).get().getParameters()[0]

        expect:
        probe(element.getAnnotationMetadata()) == EXPECTED
        element.getAnnotationMetadata().getDeclaredAnnotationNamesByStereotype(QS) == [QS]
        element.getAnnotationMetadata().getDeclaredAnnotationNamesByStereotype('') == []
        probe(field.getAnnotationMetadata()) == EXPECTED
        probe(ctorParam.getAnnotationMetadata()) == EXPECTED
        probe(methodParam.getAnnotationMetadata()) == EXPECTED
    }

    void "test declared queries by name see a repeatable annotation in the generated definition"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('repeatabledeclared.Rep', SOURCE)
        Class<? extends Annotation> q = definition.getBeanType().getClassLoader().loadClass(Q) as Class<? extends Annotation>
        def metadata = [
                definition.getAnnotationMetadata(),
                definition.getInjectedFields()[0].getAnnotationMetadata(),
                definition.getConstructor().getArguments()[0].getAnnotationMetadata(),
                definition.getExecutableMethods()[0].getArguments()[0].getAnnotationMetadata(),
        ]

        expect:
        metadata.size() == 4
        metadata.every { it.hasDeclaredAnnotation(q) }
        metadata.every { it.hasDeclaredStereotype(q) }
        probe(metadata[0]) == EXPECTED
        probe(metadata[1]) == EXPECTED
        probe(metadata[2]) == EXPECTED
        probe(metadata[3]) == EXPECTED
    }

    private static Map<String, Object> probe(AnnotationMetadata metadata) {
        [
                hasDeclaredAnnotationQs        : metadata.hasDeclaredAnnotation(QS),
                hasAnnotationQs                : metadata.hasAnnotation(QS),
                annotationNamesByStereotype    : metadata.getAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER),
                declaredAnnotationValuesByNameQ: metadata.getDeclaredAnnotationValuesByName(Q)*.stringValue()*.get(),
                annotationValuesByNameQ        : metadata.getAnnotationValuesByName(Q)*.stringValue()*.get(),
                declaredAnnotationNamesByStereotype: metadata.getDeclaredAnnotationNamesByStereotype(AnnotationUtil.QUALIFIER),
                hasDeclaredAnnotationQ         : metadata.hasDeclaredAnnotation(Q),
                hasDeclaredStereotypeQ         : metadata.hasDeclaredStereotype(Q),
                hasAnnotationQ                 : metadata.hasAnnotation(Q),
                hasStereotypeQ                 : metadata.hasStereotype(Q),
        ]
    }
}
