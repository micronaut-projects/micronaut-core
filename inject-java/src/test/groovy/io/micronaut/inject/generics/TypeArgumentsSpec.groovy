package io.micronaut.inject.generics

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.inject.BeanDefinition

class TypeArgumentsSpec extends AbstractTypeElementSpec {

    void "test type arguments are passed through to the parent"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ChainA','''\
package test;

import jakarta.inject.Singleton;

@Singleton
class ChainA extends ChainB<Boolean> {
}

class ChainB<A> extends ChainC<A, Number, Integer> {
}

abstract class ChainC<A, B, E> implements ChainD<A, B, String, E> {
}

interface ChainD<A, B, C, E> extends ChainE<A, B, C, Byte> {
}

interface ChainE<A, B, C, D> {
}
''')

        expect:
        definition.getTypeArguments("test.ChainB").size() == 1
        definition.getTypeArguments("test.ChainB")[0].type == Boolean
        definition.getTypeArguments("test.ChainC").size() == 3
        definition.getTypeArguments("test.ChainC")[0].type == Boolean
        definition.getTypeArguments("test.ChainC")[1].type == Number
        definition.getTypeArguments("test.ChainC")[2].type == Integer
        definition.getTypeArguments("test.ChainD").size() == 4
        definition.getTypeArguments("test.ChainD")[0].type == Boolean
        definition.getTypeArguments("test.ChainD")[1].type == Number
        definition.getTypeArguments("test.ChainD")[2].type == String
        definition.getTypeArguments("test.ChainD")[3].type == Integer
        definition.getTypeArguments("test.ChainE").size() == 4
        definition.getTypeArguments("test.ChainE")[0].type == Boolean
        definition.getTypeArguments("test.ChainE")[1].type == Number
        definition.getTypeArguments("test.ChainE")[2].type == String
        definition.getTypeArguments("test.ChainE")[3].type == Byte
    }

    void "test argument is provider"() {
        def context = ApplicationContext.run(["spec.name": TypeArgumentsSpec.simpleName])
        def beanDefinition = context.getBeanDefinition(MyBean)

        expect:
        beanDefinition.requiredComponents.contains(String)
        beanDefinition.constructor.getArguments()[0].isProvider()

        cleanup:
        context.close()
    }
}
