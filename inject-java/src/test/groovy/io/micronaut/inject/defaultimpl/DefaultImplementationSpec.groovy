package io.micronaut.inject.defaultimpl

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class DefaultImplementationSpec
    extends AbstractTypeElementSpec {

    void "test pick default implementation when multiple candidates"() {
        given:
        def ctx = buildContext( '''
package test;

import jakarta.inject.Singleton;
import io.micronaut.context.annotation.DefaultImplementation;

@DefaultImplementation(TestImpl.class)
interface Test {
}

@Singleton
class TestImpl implements Test {

}

@Singleton
class TestImpl2 implements Test {

}
''')
        def cls = ctx.classLoader.loadClass('test.Test')

        expect:"no non-unique bean exception is thrown and the default impl is returned"
        ctx.getBean(cls).class.simpleName == 'TestImpl'
    }
}
