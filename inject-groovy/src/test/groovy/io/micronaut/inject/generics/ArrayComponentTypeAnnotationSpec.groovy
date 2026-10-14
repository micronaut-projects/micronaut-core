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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

/**
 * Each dimension of an array keeps the type annotations written on it, and the generated argument of an array
 * carries the annotations of its components, which {@link Argument#componentType()} answers.
 */
class ArrayComponentTypeAnnotationSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import jakarta.inject.Singleton
import java.lang.annotation.*

@Singleton
class Bean<T extends CharSequence> {

    @Executable
    String @Mark("outer") [] @Mark("middle") [] method(
        @Mark("leaf") String @Mark("outer") [] @Mark("middle") [] dimensions,
        List<@Mark("argument") String> @Mark("outer") [] parameterized,
        T @Mark("outer") [] @Mark("middle") [] variables,
        List<String @Mark("outer") [] @Mark("middle") []> arrayArgument,
        String @Mark("outer") [] outerOnly,
        String[] plain) {
        return null
    }
}

@Introspected
class Holder {
    String @Mark("outer") [] @Mark("middle") [] values
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark {
    String value()
}
'''

    void "each dimension of an array keeps its own type annotations"() {
        given:
        ClassElement element = buildClassElement('test.Bean', SOURCE)
        MethodElement method = element.getEnclosedElements(ElementQuery.ALL_METHODS.named('method')).first()
        Map<String, ClassElement> types = method.parameters.collectEntries { [it.name, it.genericType] }

        expect:
        marks(types.dimensions) == ['outer', 'middle', 'leaf']
        marks(types.dimensions.withArrayDimensions(1)) == ['middle', 'leaf']
        marks(types.dimensions.withArrayDimensions(0)) == ['leaf']
        marks(types.dimensions.toArray()) == ['-', 'outer', 'middle', 'leaf']
        marks(types.parameterized) == ['outer', '-']
        marks(types.variables) == ['outer', 'middle', '-']
        marks(types.arrayArgument.typeArguments.E) == ['outer', 'middle', '-']
        marks(types.outerOnly) == ['outer', '-']
        marks(types.plain) == ['-', '-']
        marks(method.genericReturnType) == ['outer', 'middle', '-']
    }

    void "annotating a dimension does not annotate its components"() {
        given:
        ClassElement element = buildClassElement('test.Bean', SOURCE)
        MethodElement method = element.getEnclosedElements(ElementQuery.ALL_METHODS.named('method')).first()
        ClassElement array = method.parameters.find { it.name == 'plain' }.genericType

        when:
        array.annotate('test.Outer')

        then:
        array.typeAnnotationMetadata.hasAnnotation('test.Outer')
        !array.fromArray().typeAnnotationMetadata.hasAnnotation('test.Outer')
    }

    void "the generated argument of an array carries the annotations of its components"() {
        given:
        ExecutableMethod<?, ?> method = method()

        expect:
        marks(argument(method, 'dimensions').componentType()) == ['middle', '-']
        marks(argument(method, 'variables')) == ['outer', 'middle', '-']
        argument(method, 'variables').componentType().componentType().unresolvedTypeVariable
        marks(argument(method, 'arrayArgument').typeParameters[0]) == ['outer', 'middle', '-']
        marks(method.returnType.asArgument()) == ['outer', 'middle', '-']
    }

    void "an array annotated on the array type only is written the way it was before"() {
        given:
        ExecutableMethod<?, ?> method = method()

        expect:
        ['parameterized', 'outerOnly'].every {
            Argument<?> array = argument(method, it)
            array.annotationMetadata.stringValue('test.Mark').get() == 'outer'
                && array.componentType().annotationMetadata.isEmpty()
                && !array.componentType().is(array.componentType())
        }
        argument(method, 'parameterized').componentType().typeParameters[0].annotationMetadata
            .stringValue('test.Mark').get() == 'argument'
        !argument(method, 'plain').componentType().is(argument(method, 'plain').componentType())
    }

    void "an introspected property carries the annotations of its components"() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Holder', SOURCE)

        expect:
        marks(introspection.getRequiredProperty('values', String[][]).asArgument()) == ['outer', 'middle', '-']
    }

    private ExecutableMethod<?, ?> method() {
        return buildBeanDefinition('test.Bean', SOURCE).executableMethods.find { it.methodName == 'method' }
    }

    private static Argument<?> argument(ExecutableMethod<?, ?> method, String name) {
        return method.arguments.find { it.name == name }
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
