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
package io.micronaut.reflection

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.BeanDefinition
import spock.lang.AutoCleanup
import spock.lang.Shared

/**
 * A bean read reflectively must not disagree with the same bean compiled about its own type arguments: which
 * of the parameters its type declares it leaves unbound, and which it binds to a type that was written there.
 *
 * <p>Each definition is rendered whole - the bean type as an argument, and the arguments it gives every generic
 * type in its hierarchy - so a difference anywhere in the description fails here.</p>
 *
 * <p>One shape of the issue has no reflective counterpart and so is absent: a bean type written raw. A
 * reflective definition is built from a {@link Class}, which is a declaration rather than a usage of a type,
 * so there is no raw bean type for it to read; a raw <em>super</em> type, which a class can name, is here.</p>
 */
class DefinitionTypeArgumentParitySpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import jakarta.inject.Singleton;

class Box<B extends Number> {}

@Singleton
class OpenBox<T extends Number> extends Box<T> {}

@Singleton
class ClosedBox extends Box<Integer> {}

@Singleton
@SuppressWarnings("rawtypes")
class RawSuperBox extends Box {}
'''

    @Shared @AutoCleanup ApplicationContext context

    def setupSpec() {
        context = buildContext('test.OpenBox', SOURCE)
    }

    /**
     * What the argument erases to, whether it is raw, the variable it stands for with its declared bounds, and
     * the same of each type argument.
     */
    private static String render(Argument<?> argument) {
        StringBuilder rendered = new StringBuilder(argument.type.simpleName)
        if (argument.isRawType()) {
            rendered.append('!raw')
        }
        if (argument instanceof GenericPlaceholder) {
            rendered.append('<var=').append(((GenericPlaceholder<?>) argument).variableName)
                    .append(' bounds=').append(((GenericPlaceholder<?>) argument).bounds*.type*.simpleName)
                    .append('>')
        }
        if (argument.typeParameters.length > 0) {
            rendered.append('(').append(argument.typeParameters.collect { render(it) }.join(', ')).append(')')
        }
        return rendered.toString()
    }

    private static Map<String, String> describe(BeanDefinition<?> definition, Class<?> boxType) {
        [
                'as an argument': render(definition.asArgument()),
                'named after'   : definition.asArgument().typeVariables.keySet().toList().toString(),
                'given to Box'  : definition.getTypeArguments(boxType).collect { render(it) }.toString(),
                'given to self' : definition.getTypeArguments(definition.beanType).collect { render(it) }.toString()
        ]
    }

    void "a bean read reflectively says the same of its own type arguments as the same bean compiled"() {
        given:
        Class<?> boxType = context.classLoader.loadClass('test.Box')
        Map<String, String> generatedShapes = [:]
        Map<String, String> reflectiveShapes = [:]

        when:
        context.getBeanDefinitions(boxType).each { generated ->
            def reflective = ReflectionBeanDefinition.of(generated.beanType)
            describe(generated, boxType).each { key, value ->
                generatedShapes["$generated.beanType.simpleName $key"] = value
            }
            describe(reflective, boxType).each { key, value ->
                reflectiveShapes["$generated.beanType.simpleName $key"] = value
            }
        }

        then:
        generatedShapes == reflectiveShapes

        and: 'and the description they agree on is the one the source says'
        generatedShapes['OpenBox as an argument'] == 'OpenBox(Number<var=T bounds=[Number]>)'
        generatedShapes['OpenBox given to Box'] == '[Number<var=T bounds=[Number]>]'
        generatedShapes['OpenBox named after'] == '[T]'

        and: 'a binding of a concrete type is an ordinary argument, and leaves the bean type without one'
        generatedShapes['ClosedBox as an argument'] == 'ClosedBox'
        generatedShapes['ClosedBox given to Box'] == '[Integer]'

        and: 'a raw super type keeps the variables the super type declares, as a raw usage does'
        generatedShapes['RawSuperBox as an argument'] == 'RawSuperBox'
        generatedShapes['RawSuperBox given to Box'] == '[Number<var=B bounds=[Number]>]'
    }
}
