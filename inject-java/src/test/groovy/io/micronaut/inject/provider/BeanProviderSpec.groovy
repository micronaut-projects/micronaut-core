package io.micronaut.inject.provider

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.annotation.TestCachePuts

class BeanProviderSpec extends AbstractTypeElementSpec {

    void "test bean definition reference references correct bean type for Provider"() {
        given:
        BeanDefinitionReference definition = buildBeanDefinitionReference('test.Test','''\
package test;

import io.micronaut.inject.annotation.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class Test implements javax.inject.Provider<Foo>{

    public Foo get() {
        return new Foo();
    }
}

class Foo {}
''')
        expect:
        definition != null
        definition.getBeanType().name == 'test.Foo'
    }
}
