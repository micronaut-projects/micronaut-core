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
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

class TypeVariableBoundsSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.context.annotation.Executable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

interface Payment {}
interface Refundable {}
interface Event<T> {}

@Singleton
class Bean<T extends Payment & Refundable, S extends Payment, U, P extends List<String> & Refundable> {

    @Inject Event<T> field;

    Bean(Event<T> several,
         Event<S> one,
         Event<U> none,
         Event<P> parameterizedBound,
         Event<Payment> concrete,
         T direct) {}

    @Inject
    void inject(Event<T> injected) {}

    @Executable
    <M extends Payment & Refundable> void observe(Event<M> event, M directMethodVariable) {}

    @Executable
    Event<T> returns() { return null; }
}
'''

    @Shared BeanDefinition<?> definition

    def setupSpec() {
        definition = buildBeanDefinition('test.Bean', SOURCE)
    }

    private static List<String> bounds(Argument<?> argument) {
        argument instanceof GenericPlaceholder ? ((GenericPlaceholder<?>) argument).bounds*.type*.name : null
    }

    private Map<String, Argument<?>> constructorArguments() {
        definition.constructor.arguments.collectEntries { [it.name, it] }
    }

    private ExecutableMethod<?, ?> method(String name) {
        definition.executableMethods.find { it.methodName == name }
    }

    @Unroll
    void "the type argument of constructor parameter #name erases to #type and keeps the bounds #declared"() {
        given:
        Argument<?> typeArgument = constructorArguments()[name].typeParameters[0]

        expect:
        typeArgument.type.name == type
        typeArgument.isTypeVariable() == isVariable
        bounds(typeArgument) == declared

        where:
        name                 | type               | isVariable | declared
        'several'            | 'test.Payment'     | true       | ['test.Payment', 'test.Refundable']
        'one'                | 'test.Payment'     | true       | ['test.Payment']
        'none'               | 'java.lang.Object' | true       | ['java.lang.Object']
        'parameterizedBound' | 'java.util.List'   | true       | ['java.util.List', 'test.Refundable']
        'concrete'           | 'test.Payment'     | false      | null
    }

    void "a bound keeps its own type arguments"() {
        given:
        Argument<?> variable = constructorArguments().parameterizedBound.typeParameters[0]

        expect:
        variable.typeParameters[0].type == String

        and: 'both when the bounds are recorded'
        bounds(variable) == ['java.util.List', 'test.Refundable']
        ((GenericPlaceholder<?>) variable).bounds[0].typeParameters[0].type == String

        and: 'and when the single bound is the erasure the variable compiles to'
        Argument<?> single = constructorArguments().one.typeParameters[0]
        ((GenericPlaceholder<?>) single).bounds[0].type.name == 'test.Payment'
    }

    void "the bounds are kept for a type variable that is the argument itself"() {
        given:
        Argument<?> direct = constructorArguments().direct

        expect:
        direct.type.name == 'test.Payment'
        bounds(direct) == ['test.Payment', 'test.Refundable']
        ((GenericPlaceholder<?>) direct).variableName == 'T'
    }

    void "the bounds are kept for an injected field and an injected method parameter"() {
        given:
        Argument<?> field = definition.injectedFields.find { it.name == 'field' }.asArgument().typeParameters[0]
        def inject = definition.injectedMethods.find { it.name == 'inject' }

        expect:
        bounds(field) == ['test.Payment', 'test.Refundable']
        bounds(inject.arguments[0].typeParameters[0]) == ['test.Payment', 'test.Refundable']
    }

    void "the bounds of a method type variable are kept"() {
        given:
        Map<String, Argument<?>> arguments = method('observe').arguments.collectEntries { [it.name, it] }

        expect:
        bounds(arguments.event.typeParameters[0]) == ['test.Payment', 'test.Refundable']
        bounds(arguments.directMethodVariable) == ['test.Payment', 'test.Refundable']
    }

    void "the bounds are kept for an executable method return type"() {
        expect:
        bounds(method('returns').returnType.asArgument().typeParameters[0]) == ['test.Payment', 'test.Refundable']
    }

    void "a renamed variable keeps its bounds"() {
        given:
        Argument<?> variable = constructorArguments().several.typeParameters[0]
        Argument<?> renamed = variable.withName('other')

        expect:
        renamed instanceof GenericPlaceholder
        renamed.name == 'other'
        bounds(renamed) == ['test.Payment', 'test.Refundable']
        bounds(variable.withAnnotationMetadata(variable.annotationMetadata)) == ['test.Payment', 'test.Refundable']

        and: 'the bounds do not take part in equality'
        variable == Argument.ofTypeVariable(variable.type, variable.name)
    }
}
