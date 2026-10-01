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
package io.micronaut.inject.generics

import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class ResolvedGenericTypeAnnotationsSpec extends AbstractBeanDefinitionSpec {

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY,
            [OccurrenceVisitor.name, IntrospectedTypeElementVisitor.name].join(','))
    }

    void "resolved inherited field arguments preserve visitor annotations on each occurrence"() {
        given:
        def introspection = buildBeanIntrospection('test.Child', '''
package test

import io.micronaut.core.annotation.Introspected
import java.lang.annotation.*

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Usage {}

class Parent<T> {
    public List<T> items
    public List<T> plain
}
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Child extends Parent<String> {}
''')
        def items = introspection.getRequiredProperty('items', List).asArgument().typeParameters[0]
        def plain = introspection.getRequiredProperty('plain', List).asArgument().typeParameters[0]

        expect:
        items.type == String
        items.annotationMetadata.hasAnnotation('test.Usage')
        plain.type == String
        plain.annotationMetadata.empty
    }

    // Groovy does not expose source type-use annotations on generic occurrences. Visitors can add them
    // through the same language-neutral metadata API used by the other processors.
    static class OccurrenceVisitor implements TypeElementVisitor<Object, Object> {
        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'test.Child') {
                def occurrence = element.findField('items').get().genericType.typeArguments.values().first()
                assert occurrence instanceof GenericPlaceholderElement
                assert occurrence.resolved.present
                occurrence.annotate('test.Usage')
            }
        }
    }
}
