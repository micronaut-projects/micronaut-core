package io.micronaut.inject.factory

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.test.ClassWithReferencingExternalClass

class FactoryOfBeanWithUnresolvedClassSpec extends AbstractTypeElementSpec {

    void "test producing a bean with unresolved class references doesn't break preDestroy method search"() {
        when:
        def context = buildContext('''
package myfactory;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.inject.test.ClassWithReferencingExternalClass;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Factory
class MyFactory {
    @Bean(preDestroy = "method2")
    ClassWithReferencingExternalClass myBean() { return new ClassWithReferencingExternalClass(); }
}

''')

        context.getBean(ClassWithReferencingExternalClass)
        then:
        noExceptionThrown()
    }
}
