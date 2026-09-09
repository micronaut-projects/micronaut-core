/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

/**
 * The compiled definitions of the hierarchy that
 * {@code io.micronaut.context.RuntimeBeanDefinitionTypeArgumentsSpec} registers at runtime: the reference answer
 * that a runtime definition of the same classes has to match.
 */
class IntermediateTypeArgumentSpec extends AbstractTypeElementSpec {

    private static final String HIERARCHY = '''
package intermediate;

import jakarta.inject.Singleton;

interface Repo<T> {}

abstract class NumberRepo<X extends Number> implements Repo<X> {}

@Singleton
class IntRepo extends NumberRepo<Integer> {}

@Singleton
class DirectRepo implements Repo<Integer> {}

@Singleton
class RawRepo implements Repo {}
'''

    void "test an argument bound at an intermediate generic super type"() {
        given:
        def definition = buildBeanDefinition('intermediate.IntRepo', HIERARCHY)
        def repo = definition.beanType.classLoader.loadClass('intermediate.Repo')
        def numberRepo = definition.beanType.classLoader.loadClass('intermediate.NumberRepo')

        expect:
        definition.getTypeArguments(repo)*.type == [Integer]
        definition.getTypeArguments(numberRepo)*.type == [Integer]
    }

    void "test an argument bound directly"() {
        given:
        def definition = buildBeanDefinition('intermediate.DirectRepo', HIERARCHY)
        def repo = definition.beanType.classLoader.loadClass('intermediate.Repo')

        expect:
        definition.getTypeArguments(repo)*.type == [Integer]
    }

    void "test a raw implementation binds nothing"() {
        given:
        def definition = buildBeanDefinition('intermediate.RawRepo', HIERARCHY)
        def repo = definition.beanType.classLoader.loadClass('intermediate.Repo')

        expect: 'the unbound variable itself, which erases to Object and so matches any parameterization - the\n         runtime definition says the same thing by answering no argument at all'
        definition.getTypeArguments(repo)*.type == [Object]
    }
}
