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
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Shared
import spock.lang.Unroll

import static io.micronaut.inject.test.Placeholders.bounds
import static io.micronaut.inject.test.Placeholders.variableName

class TypeVariableBoundsSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Inject
import jakarta.inject.Singleton

interface Payment {}
interface Refundable {}
interface Event<T> {}

@Singleton
class Bean<T extends Payment & Refundable, S extends Payment, U, P extends List<String> & Refundable> {

    @Inject Event<T> field

    Bean(Event<T> several,
         Event<S> one,
         Event<U> none,
         Event<P> parameterizedBound,
         Event<Payment> concrete,
         T direct) {}

    @Inject
    void inject(Event<T> injected) {}

    @Executable
    public <M extends Payment & Refundable> void observe(Event<M> event, M directMethodVariable) {}

    @Executable
    Event<T> returns() { null }
}
'''

    @Shared BeanDefinition<?> definition

    def setupSpec() {
        definition = buildBeanDefinition('test.Bean', SOURCE)
    }

    private Map<String, Argument<?>> constructorArguments() {
        definition.constructor.arguments.collectEntries { [it.name, it] }
    }

    private ExecutableMethod<?, ?> method(String name) {
        definition.executableMethods.find { it.methodName == name }
    }

    @Unroll
    void "the type argument of constructor parameter #name erases to #type, is the variable #variable and keeps the bounds #declared"() {
        given:
        Argument<?> typeArgument = constructorArguments()[name].typeParameters[0]

        expect:
        typeArgument.type.name == type
        typeArgument.isTypeVariable() == isVariable
        bounds(typeArgument) == declared
        // the variable is named as it was declared, not after the parameter it stands in for: the T of
        // Event<T> is the class's T, whether or not its bounds had to be written out
        variableName(typeArgument) == variable

        where:
        name                 | type               | isVariable | declared                              | variable
        'several'            | 'test.Payment'     | true       | ['test.Payment', 'test.Refundable']   | 'T'
        'one'                | 'test.Payment'     | true       | ['test.Payment']                      | 'S'
        'none'               | 'java.lang.Object' | true       | ['java.lang.Object']                  | 'U'
        'parameterizedBound' | 'java.util.List'   | true       | ['java.util.List', 'test.Refundable'] | 'P'
        'concrete'           | 'test.Payment'     | false      | null                                  | null
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

    void "the bounds are kept for an injected property and an injected method parameter"() {
        given: 'the field is injected through the property setter Groovy generates for it'
        def setField = definition.injectedMethods.find { it.name == 'setField' }
        def inject = definition.injectedMethods.find { it.name == 'inject' }

        expect:
        bounds(setField.arguments[0].typeParameters[0]) == ['test.Payment', 'test.Refundable']
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

        and: 'renaming the argument does not rename the variable it stands for'
        ((GenericPlaceholder<?>) renamed).variableName == ((GenericPlaceholder<?>) variable).variableName
        ((GenericPlaceholder<?>) variable).variableName != null
        bounds(variable.withAnnotationMetadata(variable.annotationMetadata)) == ['test.Payment', 'test.Refundable']

        and: 'the bounds do not take part in equality'
        variable == Argument.ofTypeVariable(variable.type, variable.name)
    }
}
