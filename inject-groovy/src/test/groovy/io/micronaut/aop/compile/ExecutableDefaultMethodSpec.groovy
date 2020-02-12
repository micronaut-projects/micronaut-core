package io.micronaut.aop.compile

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition
import spock.lang.Ignore

//TODO: Test after Groovy 3 is supported
@Ignore("Groovy doesn't support default interface methods")
class ExecutableDefaultMethodSpec extends AbstractBeanDefinitionSpec {

    void "test executing a default interface method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyFactory$MyClass0', '''
package test

import io.micronaut.context.annotation.*
import javax.inject.*

interface SomeInterface<T> {

    String goDog(T dog)
    
    default String go() { return "go"; }
}

@Factory
class MyFactory {

    @Singleton
    @Executable
    MyClass myClass() {
        return new MyClass()
    }
}

class MyClass implements SomeInterface<String> {
    
    @Override
    String goDog(String name) {
        name
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
        beanDefinition.findMethod("goDog", String).get().invoke(instance, "rover") == "rover"
    }

}
