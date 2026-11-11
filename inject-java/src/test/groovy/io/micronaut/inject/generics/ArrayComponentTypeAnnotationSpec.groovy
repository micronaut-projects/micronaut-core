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
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

/**
 * The generated argument of an array carries the type annotations written on its components, which
 * {@link Argument#componentType()} answers, each dimension with its own.
 */
class ArrayComponentTypeAnnotationSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Introspected;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.List;

@Singleton
class Bean<T extends CharSequence> {

    @Executable
    @Mark("leaf") String @Mark("outer") [] @Mark("middle") [] method(
        @Mark("leaf") String @Mark("outer") [] @Mark("middle") [] dimensions,
        @Mark("leaf") int @Mark("outer") [] primitives,
        @Mark("leaf") List<@Mark("argument") String> [] parameterized,
        @Mark("leaf") T [] variables,
        List<@Mark("leaf") String []> arrayArgument,
        @Declared String[] declared,
        String @Mark("outer") [] outerOnly,
        String[] plain,
        List<String[]> plainArgument) {
        return null;
    }
}

@Introspected
class Holder {
    private @Mark("leaf") String @Mark("outer") [] @Mark("middle") [] values;

    public @Mark("leaf") String @Mark("outer") [] @Mark("middle") [] getValues() {
        return values;
    }

    public void setValues(@Mark("leaf") String @Mark("outer") [] @Mark("middle") [] values) {
        this.values = values;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER, ElementType.TYPE_USE})
@interface Declared {
}
'''

    void "each dimension of an array parameter carries its own annotations"() {
        given:
        ExecutableMethod<?, ?> method = method()
        Argument<?> dimensions = argument(method, 'dimensions')

        expect:
        marks(dimensions) == ['outer', 'middle', 'leaf']
        dimensions.name == 'dimensions'
        dimensions.componentType().type == String[]
        dimensions.componentType().componentType().type == String
        dimensions.componentType().componentType().componentType() == null
    }

    void "the return type of a method carries the annotations of its components"() {
        expect:
        marks(method().returnType.asArgument()) == ['outer', 'middle', 'leaf']
    }

    void "the element of a primitive array carries its annotations"() {
        given:
        Argument<?> primitives = argument(method(), 'primitives')

        expect:
        marks(primitives) == ['outer', 'leaf']
        primitives.componentType().type == int
    }

    void "the component of an array of a parameterized type keeps its type arguments"() {
        given:
        Argument<?> parameterized = argument(method(), 'parameterized')
        Argument<?> component = parameterized.componentType()

        expect:
        marks(parameterized) == ['-', 'leaf']
        component.type == List
        component.typeParameters.length == 1
        component.typeParameters[0].annotationMetadata.stringValue('test.Mark').get() == 'argument'
        parameterized.typeParameters[0].annotationMetadata.stringValue('test.Mark').get() == 'argument'
    }

    void "the component of an array of a type variable is the variable"() {
        given:
        Argument<?> variables = argument(method(), 'variables')
        Argument<?> component = variables.componentType()

        expect:
        marks(variables) == ['-', 'leaf']
        component.unresolvedTypeVariable
        component.type == CharSequence
    }

    void "an array type argument carries the annotations of its component"() {
        given:
        Argument<?> typeArgument = argument(method(), 'arrayArgument').typeParameters[0]

        expect:
        typeArgument.type == String[]
        marks(typeArgument) == ['-', 'leaf']
    }

    void "an annotation applicable to both the parameter and the type use is kept on both"() {
        given:
        Argument<?> declared = argument(method(), 'declared')

        expect:
        declared.annotationMetadata.hasAnnotation('test.Declared')
        declared.componentType().annotationMetadata.hasAnnotation('test.Declared')
    }

    void "an array annotated on the array type only is written the way it was before"() {
        given:
        Argument<?> outerOnly = argument(method(), 'outerOnly')

        expect:
        marks(outerOnly) == ['outer', '-']
        !outerOnly.componentType().is(outerOnly.componentType())
    }

    void "an array without annotations rebuilds an unannotated component"() {
        given:
        ExecutableMethod<?, ?> method = method()

        expect:
        [argument(method, 'plain'), argument(method, 'plainArgument').typeParameters[0]].every {
            Argument<?> component = it.componentType()
            component.type == String && component.annotationMetadata.isEmpty() && !component.is(it.componentType())
        }
    }

    void "the members of an introspected property carry the annotations of their components"() {
        given:
        BeanIntrospection<?> introspection = buildBeanIntrospection('test.Holder', SOURCE)

        expect:
        marks(introspection.getRequiredProperty('values', String[][]).asArgument()) == ['outer', 'middle', 'leaf']
    }

    private ExecutableMethod<?, ?> method() {
        BeanDefinition<?> definition = buildBeanDefinition('test.Bean', SOURCE)
        return definition.executableMethods.find { it.methodName == 'method' }
    }

    private static Argument<?> argument(ExecutableMethod<?, ?> method, String name) {
        return method.arguments.find { it.name == name }
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
