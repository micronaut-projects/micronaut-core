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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

/**
 * Each dimension of an array, an {@code Array} and the arrays it is an array of, keeps the type annotations
 * written on it, and the generated argument of an array carries the annotations of its components, which
 * {@link Argument#componentType()} answers.
 */
class ArrayComponentTypeAnnotationSpec extends AbstractKotlinCompilerSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton

@Singleton
open class Bean<T : CharSequence> {

    @Executable
    open fun method(
        dimensions: @Mark("outer") Array<@Mark("middle") Array<@Mark("leaf") String>>,
        primitives: @Mark("outer") IntArray,
        boxed: Array<@Mark("leaf") Int>,
        parameterized: Array<@Mark("leaf") List<@Mark("argument") String>>,
        variables: Array<@Mark("leaf") T>,
        arrayArgument: List<@Mark("outer") Array<@Mark("leaf") String>>,
        outerOnly: @Mark("outer") Array<String>,
        plain: Array<String>
    ): @Mark("outer") Array<@Mark("middle") Array<@Mark("leaf") String>>? = null
}

@Introspected
class Holder(val values: @Mark("outer") Array<@Mark("middle") Array<@Mark("leaf") String>>)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.TYPE)
annotation class Mark(val value: String)
'''

    void "each dimension of an array keeps its own type annotations"() {
        expect:
        buildClassElement('test.Bean', SOURCE) { ClassElement element ->
            MethodElement method = element.getEnclosedElements(ElementQuery.ALL_METHODS.named('method')).first()
            Map<String, ClassElement> types = method.parameters.collectEntries { [it.name, it.genericType] }
            assert marks(types.dimensions) == ['outer', 'middle', 'leaf']
            assert marks(types.dimensions.withArrayDimensions(1)) == ['middle', 'leaf']
            assert marks(types.dimensions.withArrayDimensions(0)) == ['leaf']
            assert marks(types.dimensions.toArray()) == ['-', 'outer', 'middle', 'leaf']
            assert marks(types.primitives) == ['outer', '-']
            assert marks(types.boxed) == ['-', 'leaf']
            assert marks(types.parameterized) == ['-', 'leaf']
            assert marks(types.variables) == ['-', 'leaf']
            assert marks(types.arrayArgument.typeArguments.E) == ['outer', 'leaf']
            assert marks(types.outerOnly) == ['outer', '-']
            assert marks(types.plain) == ['-', '-']
            assert marks(method.genericReturnType) == ['outer', 'middle', 'leaf']
        }
    }

    void "the generated argument of an array carries the annotations of its components"() {
        given:
        ExecutableMethod<?, ?> method = buildBeanDefinition('test.Bean', SOURCE).executableMethods.find {
            it.methodName == 'method'
        }
        Map<String, Argument<?>> arguments = method.arguments.collectEntries { [it.name, it] }

        expect:
        marks(arguments.dimensions) == ['outer', 'middle', 'leaf']
        marks(arguments.primitives) == ['outer', '-']
        marks(arguments.boxed) == ['-', 'leaf']
        arguments.boxed.componentType().type == Integer
        marks(arguments.parameterized) == ['-', 'leaf']
        arguments.parameterized.componentType().typeParameters[0].annotationMetadata
            .stringValue('test.Mark').get() == 'argument'
        marks(arguments.variables) == ['-', 'leaf']
        arguments.variables.componentType().unresolvedTypeVariable
        marks(arguments.arrayArgument.typeParameters[0]) == ['outer', 'leaf']
        marks(method.returnType.asArgument()) == ['outer', 'middle', 'leaf']
    }

    void "an array annotated on the array type only is written the way it was before"() {
        given:
        ExecutableMethod<?, ?> method = buildBeanDefinition('test.Bean', SOURCE).executableMethods.find {
            it.methodName == 'method'
        }
        Map<String, Argument<?>> arguments = method.arguments.collectEntries { [it.name, it] }

        expect:
        [arguments.primitives, arguments.outerOnly, arguments.plain].every {
            !it.componentType().is(it.componentType())
        }
    }

    void "an introspected property carries the annotations of its components"() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Holder', SOURCE)

        expect:
        marks(introspection.getRequiredProperty('values', String[][]).asArgument()) == ['outer', 'middle', 'leaf']
    }

    /**
     * The annotation of each dimension, the array first and the element last.
     */
    private static List<String> marks(ClassElement array) {
        List<String> marks = []
        for (ClassElement dimension = array; ; dimension = dimension.fromArray()) {
            marks << dimension.typeAnnotationMetadata.stringValue('test.Mark').orElse('-')
            if (!dimension.array) {
                return marks
            }
        }
    }

    /**
     * The annotation of each dimension, the array first and the element last.
     */
    private static List<String> marks(Argument<?> array) {
        List<String> marks = []
        for (Argument<?> dimension = array; dimension != null; dimension = dimension.componentType()) {
            marks << dimension.annotationMetadata.stringValue('test.Mark').orElse('-')
        }
        return marks
    }
}
