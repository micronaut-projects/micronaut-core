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
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class TypeVariableUseAnnotationsSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import java.lang.annotation.*

class Test<@Decl B extends @Bound CharSequence> {
    @Both public B field
    @DeclarationOnly public B declarationOnlyField
    @NoTarget public B noTargetField
    public B plainField
    @Both public List<B> listField
    @Both public B[] arrayField

    @Both B method(@Both B parameter) {
        return null
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE])
@interface Both {}

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER])
@interface DeclarationOnly {}

@Retention(RetentionPolicy.RUNTIME)
@interface NoTarget {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Decl {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Bound {}
'''

    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, TestVisitor.name)
        TestVisitor.check = null
        TestVisitor.checked = false
    }

    void "a type variable use reports the declaration annotations applicable to TYPE_USE"() {
        expect:
        compile { ClassElement ce ->
            def field = ce.getFields().find { it.name == 'field' }
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named('method')).get()
            assertUse(field.type)
            assertUse(field.genericType)
            assertUse(method.returnType)
            assertUse(method.genericReturnType)
            assertUse(method.parameters[0].type)
            assertUse(method.parameters[0].genericType)
        }
    }

    void "a type variable use without annotations does not report the declaration's annotations"() {
        expect:
        compile { ClassElement ce ->
            assertNoUseAnnotations(ce.getFields().find { it.name == 'plainField' }.type)
            assertNoUseAnnotations(ce.getFields().find { it.name == 'declarationOnlyField' }.type)
            // Without @Target an annotation is applicable in declaration contexts only
            assertNoUseAnnotations(ce.getFields().find { it.name == 'noTargetField' }.type)
        }
    }

    void "a declaration annotation applies to the top level use only"() {
        expect:
        compile { ClassElement ce ->
            def listField = ce.getFields().find { it.name == 'listField' }
            def arrayField = ce.getFields().find { it.name == 'arrayField' }
            assertNoUseAnnotations(listField.type.typeArguments['E'])
            assertNoUseAnnotations(listField.genericType.typeArguments['E'])
            // As for a class type, the annotation applies to the component type
            assertUse(arrayField.type.fromArray())
        }
    }

    void "the type parameter declaration keeps its own annotations"() {
        expect:
        compile { ClassElement ce ->
            def declaration = ce.declaredGenericPlaceholders[0]
            assert declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Decl')
            assert !declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Both')
        }
    }

    /**
     * Checks the element while the visitors run: the Groovy compiler copies the annotations
     * applicable to TYPE_USE to the type only when it generates the class.
     */
    private boolean compile(Closure<?> check) {
        TestVisitor.check = check
        buildClassElement('test.Test', SOURCE)
        assert TestVisitor.checked
        return true
    }

    private static void assertUse(ClassElement type) {
        assert type instanceof GenericPlaceholderElement
        assert type.genericTypeAnnotationMetadata.hasAnnotation('test.Both')
        assert !type.genericTypeAnnotationMetadata.hasAnnotation('test.Decl')
        assert type.typeAnnotationMetadata.hasAnnotation('test.Both')
        assert !type.typeAnnotationMetadata.hasAnnotation('test.Decl')
    }

    private static void assertNoUseAnnotations(ClassElement type) {
        assert type instanceof GenericPlaceholderElement
        assert !type.genericTypeAnnotationMetadata.hasAnnotation('test.Decl')
        assert !type.genericTypeAnnotationMetadata.hasAnnotation('test.Both')
        assert !type.genericTypeAnnotationMetadata.hasAnnotation('test.DeclarationOnly')
        assert !type.genericTypeAnnotationMetadata.hasAnnotation('test.NoTarget')
        assert !type.typeAnnotationMetadata.hasAnnotation('test.Decl')
    }

    static class TestVisitor implements TypeElementVisitor<Object, Object> {
        static Closure<?> check
        static boolean checked

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'test.Test' && check != null) {
                check(element)
                checked = true
            }
        }
    }
}
