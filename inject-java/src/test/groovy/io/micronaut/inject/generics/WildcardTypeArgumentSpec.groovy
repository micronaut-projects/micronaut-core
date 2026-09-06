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
import io.micronaut.core.annotation.Wildcard
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod

class WildcardTypeArgumentSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.List;
import java.util.function.Consumer;

interface Foo<T> {}
interface Bounded<T extends Number> {}
class Book {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Mark {}

@Singleton
class Bean<T> {
    @Inject Foo<?> field;

    Bean(Foo<?> unbounded,
         Foo<Object> object,
         Foo<T> variable,
         List<? extends Number> upper,
         Consumer<? super Book> lower,
         Bounded<?> implicitBound,
         List<List<? extends Number>> nested,
         List<? extends @Mark Number> annotated) {}

    @Executable
    void on(Foo<?> unbounded, List<? extends Number> upper, Consumer<? super Book> lower) {}
}
'''

    private static Wildcard.Bound wildcardOf(Argument<?> argument) {
        argument.annotationMetadata.enumValue(Wildcard, "bound", Wildcard.Bound).orElse(null)
    }

    void "a wildcard type argument is recorded on the argument it is compiled to"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Bean', SOURCE)
        Map<String, Argument<?>> arguments = definition.constructor.arguments.collectEntries { [it.name, it] }

        expect: 'the argument is the bound the wildcard was compiled to, marked as a wildcard'
        arguments.unbounded.typeParameters[0].type == Object
        wildcardOf(arguments.unbounded.typeParameters[0]) == Wildcard.Bound.NONE

        arguments.upper.typeParameters[0].type == Number
        wildcardOf(arguments.upper.typeParameters[0]) == Wildcard.Bound.UPPER

        arguments.lower.typeParameters[0].type.name == 'test.Book'
        wildcardOf(arguments.lower.typeParameters[0]) == Wildcard.Bound.LOWER

        arguments.implicitBound.typeParameters[0].type == Object
        wildcardOf(arguments.implicitBound.typeParameters[0]) == Wildcard.Bound.NONE

        and: 'a type argument that is not a wildcard is not marked, whether a type or a type variable'
        arguments.object.typeParameters[0].type == Object
        !arguments.object.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        arguments.variable.typeParameters[0].type == Object
        arguments.variable.typeParameters[0].isTypeVariable()
        !arguments.variable.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)

        and: 'a nested wildcard is recorded at its own level only'
        !arguments.nested.typeParameters[0].annotationMetadata.hasAnnotation(Wildcard)
        arguments.nested.typeParameters[0].type == List
        arguments.nested.typeParameters[0].typeParameters[0].type == Number
        wildcardOf(arguments.nested.typeParameters[0].typeParameters[0]) == Wildcard.Bound.UPPER

        and: 'a type-use annotation on the bound is kept next to the wildcard'
        wildcardOf(arguments.annotated.typeParameters[0]) == Wildcard.Bound.UPPER
        arguments.annotated.typeParameters[0].annotationMetadata.hasAnnotation('test.Mark')

        and: 'the wildcard is not visible on the enclosing argument'
        !arguments.upper.annotationMetadata.hasAnnotation(Wildcard)
    }

    void "a wildcard is recorded for an executable method parameter and an injected field"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Bean', SOURCE)
        ExecutableMethod<?, ?> method = definition.executableMethods.find { it.methodName == 'on' }
        Map<String, Argument<?>> arguments = method.arguments.collectEntries { [it.name, it] }

        expect:
        wildcardOf(arguments.unbounded.typeParameters[0]) == Wildcard.Bound.NONE
        wildcardOf(arguments.upper.typeParameters[0]) == Wildcard.Bound.UPPER
        arguments.upper.typeParameters[0].type == Number
        wildcardOf(arguments.lower.typeParameters[0]) == Wildcard.Bound.LOWER
        arguments.lower.typeParameters[0].type.name == 'test.Book'

        and:
        def field = definition.injectedFields.find { it.name == 'field' }
        wildcardOf(field.asArgument().typeParameters[0]) == Wildcard.Bound.NONE
    }

    void "a wildcard is recorded in the type arguments of a bean and of an introspected property"() {
        given:
        BeanDefinition<?> definition = buildBeanDefinition('test.Bean', '''
package test;

import jakarta.inject.Singleton;
import java.util.List;

interface Foo<T> {}

@Singleton
class Bean implements Foo<List<? extends Number>> {}
''')
        def introspection = buildBeanIntrospection('test.Bean', '''
package test;

import io.micronaut.core.annotation.Introspected;
import java.util.List;

@Introspected
class Bean {
    private List<? extends Number> numbers;
    public List<? extends Number> getNumbers() { return numbers; }
    public void setNumbers(List<? extends Number> numbers) { this.numbers = numbers; }
}
''')

        expect:
        wildcardOf(definition.getTypeArguments('test.Foo')[0].typeParameters[0]) == Wildcard.Bound.UPPER
        definition.getTypeArguments('test.Foo')[0].typeParameters[0].type == Number

        and:
        def property = introspection.getRequiredProperty('numbers', List)
        property.asArgument().typeParameters[0].type == Number
        wildcardOf(property.asArgument().typeParameters[0]) == Wildcard.Bound.UPPER
    }
}
