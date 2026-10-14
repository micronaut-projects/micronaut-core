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
package io.micronaut.context

import io.micronaut.context.generics.IntRepo
import io.micronaut.context.generics.KeyedDefinition
import io.micronaut.context.generics.Marker
import io.micronaut.context.generics.NumberRepo
import io.micronaut.context.generics.Plain
import io.micronaut.context.generics.Repo
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import java.util.function.Supplier

/**
 * {@code BeanDefinition#getTypeClosure()} for a definition that does not record it: a runtime definition
 * resolves its hierarchy reflectively and answers the whole closure, while the interface default answers
 * what it can without loading a class by name.
 */
class RuntimeBeanDefinitionTypeClosureSpec extends Specification {

    private static <B> RuntimeBeanDefinition<B> definitionOf(Class<B> type) {
        RuntimeBeanDefinition.builder(type, { type.getDeclaredConstructor().newInstance() } as Supplier)
                .singleton(true)
                .build()
    }

    void 'a runtime definition answers the whole hierarchy, the bean type first and Object last'() {
        given:
        def closure = definitionOf(IntRepo).typeClosure

        expect:
        closure*.type == [IntRepo, NumberRepo, Repo, Object]
        closure[1].typeParameters*.type == [Integer]
        closure[2].typeParameters*.type == [Integer]
        closure[0].typeParameters.length == 0
        closure[3] == Argument.OBJECT_ARGUMENT

        and: 'a hierarchy without type arguments is recorded all the same'
        definitionOf(Plain).typeClosure*.type == [Plain, Marker, Object]
    }

    void 'the interface default answers the bean type, the keys it can turn into an exposed type, and Object'() {
        given: 'a definition that records keys and exposes one of them'
        BeanDefinition<IntRepo> definition = new KeyedDefinition()

        expect: 'the exposed super type is placed with its recorded arguments; the key that is not exposed cannot be loaded and is left out'
        definition.typeClosure*.type == [IntRepo, Repo, Object]
        definition.typeClosure[1].typeParameters*.type == [Integer]
    }
}
