package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.aop.exceptions.UnimplementedAdviceException
import io.micronaut.context.BeanContext
import io.micronaut.inject.AdvisedBeanType
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.kotlin.processing.aop.introduction.NotImplementedAdvice
import spock.lang.Specification

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

import static io.micronaut.kotlin.processing.KotlinCompiler.*

class IntroductionAnnotationSpec extends Specification {

    void 'test unimplemented introduction advice'() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.NotImplemented

@NotImplemented
interface MyBean {
    fun test()
}
''')
        def context = BeanContext.run()
        def bean = ((BeanFactory) beanDefinition).build(context, beanDefinition)

        when:
        bean.test()

        then:
        beanDefinition instanceof AdvisedBeanType
        beanDefinition.interceptedType.name == 'test.MyBean'
        thrown(UnimplementedAdviceException)

        cleanup:
        context.close()
    }

    void 'test unimplemented introduction advice on abstract class with concrete methods'() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.NotImplemented
import io.micronaut.context.annotation.*
import io.micronaut.kotlin.processing.aop.simple.Mutating

@NotImplemented
abstract class MyBean {

    abstract fun test()
    
    fun test2(): String {
        return "good"
    }
    
    @Mutating("arg")
    open fun test3(arg: String): String {
        return arg
    }
}
''')
        def context = BeanContext.run()
        def bean = ((BeanFactory) beanDefinition).build(context, beanDefinition)
        def notImplementedAdvice = context.getBean(NotImplementedAdvice)

        when:
        bean.test()

        then:
        thrown(UnimplementedAdviceException)
        notImplementedAdvice.invoked

        when:
        notImplementedAdvice.invoked = false

        then:
        bean.test2() == 'good'
        bean.test3() == 'changed'
        !notImplementedAdvice.invoked

        cleanup:
        context.close()
    }

    void "test @Min annotation"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub
import io.micronaut.context.annotation.Executable
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

interface MyInterface{
    @Executable
    fun save(@NotBlank name: String, @Min(1L) age: Int)
    
    @Executable
    fun saveTwo(@Min(1L) name: String)
}


@Stub
@jakarta.inject.Singleton
interface MyBean: MyInterface
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 2
        beanDefinition.executableMethods[0].methodName == 'save'
        beanDefinition.executableMethods[0].returnType.type == void.class
        beanDefinition.executableMethods[0].arguments[0].getAnnotationMetadata().hasAnnotation(NotBlank)
        beanDefinition.executableMethods[0].arguments[1].getAnnotationMetadata().hasAnnotation(Min)
        beanDefinition.executableMethods[0].arguments[1].getAnnotationMetadata().getValue(Min, Integer).get() == 1

        beanDefinition.executableMethods[1].methodName == 'saveTwo'
        beanDefinition.executableMethods[1].returnType.type == void.class
        beanDefinition.executableMethods[1].arguments[0].getAnnotationMetadata().hasAnnotation(Min)
    }

}
