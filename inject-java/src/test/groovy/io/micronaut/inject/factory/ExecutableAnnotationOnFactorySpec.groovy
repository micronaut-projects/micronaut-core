/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.inject.factory

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

import java.util.function.Function

class ExecutableAnnotationOnFactorySpec extends AbstractTypeElementSpec {
    void "test executable annotation on factory"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc0','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@Factory
class Test {

    @Bean
    @Executable
    java.util.function.Function<String, Integer> myFunc() {
        return (str) -> 10;
    }
}

''')
        expect:
        definition != null
        definition.findMethod("apply", String.class).isPresent()
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }
}
