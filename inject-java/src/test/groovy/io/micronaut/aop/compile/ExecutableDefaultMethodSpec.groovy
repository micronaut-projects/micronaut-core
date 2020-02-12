package io.micronaut.aop.compile

import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition;

class ExecutableDefaultMethodSpec extends AbstractTypeElementSpec {

    void "test executing a default interface method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyFactory$MyClass0', '''
package test;

import io.micronaut.context.annotation.*;
import javax.inject.*;

interface SomeInterface {

    String goDog();
    default String go() { return "go"; }
}

@Factory
class MyFactory {

    @Singleton
    @Executable
    MyClass myClass() {
        return new MyClass();
    }
}

class MyClass implements SomeInterface {
    
    @Override
    public String goDog() {
        return "go";
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null

        when:
        Object instance = beanDefinition.class.classLoader.loadClass('test.MyClass').newInstance()

        then:
        beanDefinition.findMethod("go").get().invoke(instance) == "go"
        beanDefinition.findMethod("goDog").get().invoke(instance) == "go"
    }
}
