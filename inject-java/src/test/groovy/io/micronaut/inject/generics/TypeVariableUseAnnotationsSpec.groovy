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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement

class TypeVariableUseAnnotationsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import java.lang.annotation.*;
import java.util.List;

class Test<@Decl B extends @Bound CharSequence> {
    @Both B field;
    @DeclarationOnly B declarationOnlyField;
    @NoTarget B noTargetField;
    B plainField;
    @Decl B typeUseField;
    @Both List<B> listField;
    @Both B[] arrayField;

    @Both B method(@Both B parameter) {
        return null;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE_USE})
@interface Both {}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
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

    void "a type variable use reports the declaration annotations applicable to TYPE_USE"() {
        expect:
        buildClassElement(SOURCE) { ClassElement ce ->
            def field = ce.getFields().find { it.name == 'field' }
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named('method')).get()
            assertUse(field.type)
            assertUse(field.genericType)
            assertUse(method.returnType)
            assertUse(method.genericReturnType)
            assertUse(method.parameters[0].type)
            assertUse(method.parameters[0].genericType)
            true
        }
    }

    void "a type variable use without annotations does not report the declaration's annotations"() {
        expect:
        buildClassElement(SOURCE) { ClassElement ce ->
            assertNoUseAnnotations(ce.getFields().find { it.name == 'plainField' }.type)
            assertNoUseAnnotations(ce.getFields().find { it.name == 'declarationOnlyField' }.type)
            // Without @Target an annotation is applicable in declaration contexts only
            assertNoUseAnnotations(ce.getFields().find { it.name == 'noTargetField' }.type)
            true
        }
    }

    void "a TYPE_USE only annotation on a type variable use is kept"() {
        expect:
        buildClassElement(SOURCE) { ClassElement ce ->
            def type = ce.getFields().find { it.name == 'typeUseField' }.type
            assert type.genericTypeAnnotationMetadata.hasAnnotation('test.Decl')
            assert type.typeAnnotationMetadata.hasAnnotation('test.Decl')
            true
        }
    }

    void "a declaration annotation applies to the top level use only"() {
        expect:
        buildClassElement(SOURCE) { ClassElement ce ->
            def listField = ce.getFields().find { it.name == 'listField' }
            def arrayField = ce.getFields().find { it.name == 'arrayField' }
            assertNoUseAnnotations(listField.type.typeArguments['E'])
            // As for a class type, the annotation applies to the component type
            assertUse(arrayField.type.fromArray())
            true
        }
    }

    void "the type parameter declaration keeps its own annotations"() {
        expect:
        buildClassElement(SOURCE) { ClassElement ce ->
            def declaration = ce.declaredGenericPlaceholders[0]
            assert declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Decl')
            assert !declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Both')
            true
        }
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
}
