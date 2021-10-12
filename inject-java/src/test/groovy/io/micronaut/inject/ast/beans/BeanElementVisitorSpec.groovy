package io.micronaut.inject.ast.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Prototype
import io.micronaut.inject.visitor.BeanElementVisitor

class BeanElementVisitorSpec extends AbstractTypeElementSpec {

    void "test produce another bean from a bean element visitor"() {
        given:
        def context = buildContext('''
package testbe2;

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

@Prototype
class Excluded {
    
}
''')

        expect:
        getBean(context, 'testbe2.Test')
        context.getBean(String) == 'test' // produced from TestBeanElementVisitor
        !context.containsBean(context.classLoader.loadClass('testbe2.Excluded'))

        cleanup:
        context.close()

    }

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

        expect:
        BeanElementVisitor.VISITORS.first() instanceof TestBeanElementVisitor
        TestBeanElementVisitor visitor = BeanElementVisitor.VISITORS.first()
        BeanElement beanElement = visitor.theBeanElement
        visitor.terminated
        visitor.initialized
        beanElement != null
        beanElement.scope.get() == Prototype.name
        beanElement.qualifiers.size() == 1
        beanElement.injectionPoints.size() == 2
        beanElement.declaringClass.name == 'testbe.Test'
        beanElement.producingElement.name == 'testbe.Test'
        beanElement.beanTypes*.getName() as Set == ['testbe.Test', 'java.lang.Runnable'] as Set

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

        expect:
        BeanElementVisitor.VISITORS.first() instanceof TestBeanElementVisitor
        BeanElement beanElement = BeanElementVisitor.VISITORS.first().theBeanElement

        beanElement != null
        beanElement.scope.get() == Prototype.name
        beanElement.qualifiers.size() == 0
        beanElement.injectionPoints.size() == 0
        beanElement.declaringClass.name == 'testbe.TestFactory'
        beanElement.producingElement.name == 'test'
        beanElement.beanTypes*.getName() as Set == ['testbe.Test'] as Set

    }
}
