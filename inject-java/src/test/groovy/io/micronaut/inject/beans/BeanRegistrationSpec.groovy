package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.BeanRegistration


class BeanRegistrationSpec extends AbstractTypeElementSpec {

    void 'test inject bean registrations'() {

        given:
        def className = 'beanreg.Test'
        def context = buildContext(className, '''
package beanreg;

import jakarta.inject.*;
import io.micronaut.context.BeanRegistration;
import java.util.*;

@Singleton
class Test {
    @Inject
    Collection<BeanRegistration<Foo>> fieldRegistrations;
    
    @Inject
    BeanRegistration<Foo>[] fieldArrayRegistrations;
    
    Collection<BeanRegistration<Foo>> registrations;
    
    List<BeanRegistration<Foo>> methodRegistrations;
     
    BeanRegistration<Foo> primaryBean;
    
    @Named("two")
    @Inject
    BeanRegistration<Foo> secondaryBean;
     
    Test(Collection<BeanRegistration<Foo>> registrations, BeanRegistration<Foo> primaryBean) {
        this.registrations = registrations;
        this.primaryBean = primaryBean;
    }
    
    @Inject
    void setRegs(List<BeanRegistration<Foo>> mrs) {
        this.methodRegistrations = mrs;
    }
}

interface Foo {}

@Singleton
@io.micronaut.context.annotation.Primary
class Foo1 implements Foo {}

@Singleton
@Named("two")
class Foo2 implements Foo {}
''')

        def bean = getBean(context, className)

        Collection<BeanRegistration> registrations = bean.registrations
        Collection<BeanRegistration> fieldRegistrations = bean.fieldRegistrations
        Collection<BeanRegistration> methodRegistrations = bean.methodRegistrations
        Collection<BeanRegistration> fieldArrayRegistrations = bean.fieldArrayRegistrations.toList()

        expect:
        bean.primaryBean.bean.getClass().name == 'beanreg.Foo1'
        bean.secondaryBean.bean.getClass().name == 'beanreg.Foo2'
        registrations.size() == 2
        fieldRegistrations.size() == 2
        fieldRegistrations == registrations
        fieldRegistrations as List == methodRegistrations
        fieldRegistrations as List == fieldArrayRegistrations
        registrations.any { it.bean.getClass().name == 'beanreg.Foo1'}
        registrations.any { it.bean.getClass().name == 'beanreg.Foo2'}

        cleanup:
        context.close()
    }
}
