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
import spock.lang.Shared

class RawTypeArgumentSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Inject
import jakarta.inject.Singleton

class Box<B extends Number> {}

@Singleton
class Holder<T extends Number> {

    @Inject public List raw
    @Inject public List<T> variable
    @Inject public List<String> concrete
    @Inject public List<Object> object
    @Inject public List<?> wildcard
    @Inject public Box box
    @Inject public Map<String, List> nested

    Holder(List rawConstructorArgument, List<T> variableConstructorArgument) {}

    @Inject
    void inject(List rawMethodArgument) {}

    @Executable
    List returnsRaw() { null }
}
'''

    @Shared BeanDefinition<?> definition

    def setupSpec() {
        definition = buildBeanDefinition('test.Holder', SOURCE)
    }

    private Argument<?> field(String name) {
        definition.injectedFields.find { it.name == name }.asArgument()
    }

    void "a raw type is told apart from a usage written with a type variable"() {
        given:
        Argument<?> raw = field('raw')
        Argument<?> variable = field('variable')

        expect: 'both keep the type parameters they compile to, and only one of them is raw'
        raw.isRawType()
        !variable.isRawType()

        and: 'which is the only thing that tells them apart: the placeholder is the same shape either way'
        raw.typeParameters.length == 1
        variable.typeParameters.length == 1
        raw.typeParameters[0] instanceof GenericPlaceholder
        variable.typeParameters[0] instanceof GenericPlaceholder
        raw.typeParameters[0].isTypeVariable()
        variable.typeParameters[0].isTypeVariable()

        and: 'and the placeholder of the raw usage is the one the declaring type declares'
        ((GenericPlaceholder<?>) raw.typeParameters[0]).variableName == 'E'
        ((GenericPlaceholder<?>) variable.typeParameters[0]).variableName == 'T'
    }

    void "a type written with its type arguments is not raw"() {
        expect:
        !field('concrete').isRawType()
        !field('object').isRawType()
        !field('wildcard').isRawType()

        and: 'a raw usage is told apart from List<Object>, which erases to the same thing'
        field('raw').isRawType()
        field('raw').typeParameters[0].type == Object
        field('object').typeParameters[0].type == Object
    }

    void "a raw usage keeps the bounds the declaring type declares"() {
        given:
        Argument<?> box = field('box')

        expect:
        box.isRawType()
        box.typeParameters[0].type == Number
        ((GenericPlaceholder<?>) box.typeParameters[0]).variableName == 'B'
    }

    void "a raw type argument of a parameterized type is raw"() {
        given:
        Argument<?> nested = field('nested')

        expect:
        !nested.isRawType()
        !nested.typeParameters[0].isRawType()
        nested.typeParameters[1].isRawType()
        nested.typeParameters[1].type == List
    }

    void "a raw constructor argument, method argument and return type are raw"() {
        given:
        Map<String, Argument<?>> constructorArguments = definition.constructor.arguments.collectEntries { [it.name, it] }
        Argument<?> methodArgument = definition.injectedMethods.find { it.name == 'inject' }.arguments[0]
        Argument<?> returnType = definition.executableMethods.find { it.methodName == 'returnsRaw' }.returnType.asArgument()

        expect:
        constructorArguments.rawConstructorArgument.isRawType()
        !constructorArguments.variableConstructorArgument.isRawType()
        methodArgument.isRawType()
        returnType.isRawType()
    }

    void "renaming a raw argument keeps it raw"() {
        given:
        Argument<?> raw = field('raw')

        expect:
        raw.withName('other').isRawType()
        raw.withName('other').name == 'other'
        raw.withAnnotationMetadata(raw.annotationMetadata).isRawType()

        and: 'rawness does not take part in equality, as the bounds of a variable do not'
        raw == Argument.of(List, 'raw', raw.typeParameters)
    }
}
