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
package io.micronaut.kotlin.processing.inject.generics

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MethodElement

class TypeVariableUseAnnotationsSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

abstract class Test<@Decl B> {
    abstract val annotatedProperty: @Both B
    abstract val plainProperty: B

    abstract fun annotated(parameter: @Both B): @Both B
    abstract fun plain(parameter: B): B
    abstract fun list(): List<B>
    abstract fun annotatedList(): List<@Both B>
    abstract fun <@Decl T> method(parameter: T): @Both T
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
annotation class Both

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE_PARAMETER)
annotation class Decl
'''

    void "a type parameter use reports its own annotations"() {
        expect:
        buildClassElementMapped('test.Test', SOURCE) { ClassElement ce ->
            def method = method(ce, 'annotated')
            assertUse(method.returnType)
            assertUse(method.genericReturnType)
            assertUse(method.parameters[0].type)
            assertUse(method.parameters[0].genericType)
            def property = ce.beanProperties.find { it.name == 'annotatedProperty' }
            assertUse(property.type)
            assertUse(property.genericType)
            true
        }
    }

    void "a type parameter use without annotations does not report the declaration's annotations"() {
        expect:
        buildClassElementMapped('test.Test', SOURCE) { ClassElement ce ->
            def method = method(ce, 'plain')
            assertNoUseAnnotations(method.returnType)
            assertNoUseAnnotations(method.genericReturnType)
            assertNoUseAnnotations(method.parameters[0].type)
            assertNoUseAnnotations(method.parameters[0].genericType)
            assertNoUseAnnotations(ce.beanProperties.find { it.name == 'plainProperty' }.type)
            true
        }
    }

    void "a type parameter used as a type argument reports the annotations of that use"() {
        expect:
        buildClassElementMapped('test.Test', SOURCE) { ClassElement ce ->
            assertNoUseAnnotations(method(ce, 'list').returnType.typeArguments['E'])
            assertUse(method(ce, 'annotatedList').returnType.typeArguments['E'])
            true
        }
    }

    void "the type parameter declaration keeps its own annotations"() {
        expect:
        buildClassElementMapped('test.Test', SOURCE) { ClassElement ce ->
            def declaration = ce.declaredGenericPlaceholders[0]
            assert declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Decl')
            assert !declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Both')
            true
        }
    }

    void "a method type parameter declaration keeps its own annotations"() {
        expect:
        buildClassElementMapped('test.Test', SOURCE) { ClassElement ce ->
            def method = method(ce, 'method')
            def declaration = method.declaredTypeVariables[0]
            assert declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Decl')
            assert !declaration.genericTypeAnnotationMetadata.hasAnnotation('test.Both')
            assertNoUseAnnotations(method.parameters[0].type)
            assertNoUseAnnotations(method.parameters[0].genericType)
            assertUse(method.returnType)
            assertUse(method.genericReturnType)
            true
        }
    }

    private static MethodElement method(ClassElement ce, String name) {
        return ce.getEnclosedElement(ElementQuery.ALL_METHODS.named(name)).get()
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
        assert !type.typeAnnotationMetadata.hasAnnotation('test.Decl')
        assert !type.typeAnnotationMetadata.hasAnnotation('test.Both')
    }
}
