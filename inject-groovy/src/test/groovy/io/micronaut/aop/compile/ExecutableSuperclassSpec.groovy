package io.micronaut.aop.compile

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.BeanDefinition

class ExecutableSuperclassSpec extends AbstractBeanDefinitionSpec {

    void "test super class properties are properly recognized"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.SuperClassFactory', '''
package test

import io.micronaut.context.annotation.*

@Factory
class SuperClassFactory extends io.micronaut.aop.compile.SuperClass {
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null

        when:
        Object instance = beanDefinition.class.classLoader.loadClass('test.SuperClassFactory').newInstance()

        then:
        beanDefinition.findMethod("myBool").get().invoke(instance) == false
        beanDefinition.findMethod("myInt").get().invoke(instance) == 12
        beanDefinition.findMethod("myDouble").get().invoke(instance) == 15.5
    }

}
