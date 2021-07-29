package io.micronaut.inject.ast.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Prototype

class BeanElementVisitorSpec extends AbstractTypeElementSpec {

    void "test visit bean element for simple bean"() {
        given:
        buildBeanDefinition('testbe.Test', '''
package testbe;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Prototype
@Named("blah")
class Test implements Runnable {
    @Inject ConversionService<?> conversionService;
    
    @Inject
    void setEnvironment(Environment environment) {
        
    }
    
    @Override public void run() {
    
    }
}
''')
        BeanElement beanElement = TestBeanElementVisitor.theBeanElement

        expect:
        TestBeanElementVisitor.first
        !SecondBeanElementVisitor.first
        beanElement != null
        beanElement.scope.get() == Prototype.name
        beanElement.qualifiers.size() == 1
        beanElement.injectionPoints.size() == 2
        beanElement.declaringClass.name == 'testbe.Test'
        beanElement.producingElement.name == 'testbe.Test'
        beanElement.beanTypes == ['testbe.Test', 'java.lang.Runnable'] as Set

    }

    void "test visit bean element for factory bean"() {
        given:
        buildBeanDefinition('testbe.Test', '''
package testbe;

import io.micronaut.context.annotation.Bean;import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.Environment;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Prototype
@Factory
class TestFactory implements Runnable {
    @Inject ConversionService<?> conversionService;
    
    @Inject
    void setEnvironment(Environment environment) {
        
    }
    
    @Bean
    Test test() {
        return new Test();
    }
    
    @Override public void run() {
    
    }
}

class Test {}
''')
        BeanElement beanElement = TestBeanElementVisitor.theBeanElement

        expect:
        beanElement != null
        beanElement.scope.get() == Prototype.name
        beanElement.qualifiers.size() == 0
        beanElement.injectionPoints.size() == 0
        beanElement.declaringClass.name == 'testbe.TestFactory'
        beanElement.producingElement.name == 'test'
        beanElement.beanTypes == ['testbe.Test'] as Set

    }
}
