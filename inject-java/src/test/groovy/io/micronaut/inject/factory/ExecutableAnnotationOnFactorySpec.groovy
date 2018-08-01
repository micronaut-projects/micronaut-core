package io.micronaut.inject.factory

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

import java.util.function.Function

class ExecutableAnnotationOnFactorySpec extends AbstractTypeElementSpec {
    void "test executable annotation on factory"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.Test$MyFunc','''\
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
        definition.findMethod("apply", Object.class).isPresent()
        definition.getTypeArguments(Function).size() == 2
        definition.getTypeArguments(Function)[0].name == 'T'
        definition.getTypeArguments(Function)[1].name == 'R'
        definition.getTypeArguments(Function)[0].type == String
        definition.getTypeArguments(Function)[1].type == Integer
    }
}
