package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Blocking
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class InheritedAnnotationMetadataSpec extends Specification {

    void "test that annotation metadata is inherited from overridden methods for introduction advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Blocking
import io.micronaut.kotlin.processing.aop.introduction.Stub

@Stub
@jakarta.inject.Singleton
interface MyBean: MyInterface {
    override fun someMethod(): String
}

interface MyInterface {
    @Blocking
    @Executable
    fun someMethod(): String
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].hasAnnotation(Blocking)
        !beanDefinition.executableMethods[0].hasDeclaredAnnotation(Blocking)
    }

    void "test that annotation metadata is inherited from overridden methods for around advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.simple.Mutating
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Blocking

@Mutating("someVal")
@jakarta.inject.Singleton
open class MyBean(@Value("\\${foo.bar}") private val myValue: String): MyInterface {

    override fun someMethod(): String {
        return myValue
    }
}

interface MyInterface {
    @Blocking
    @Executable
    fun someMethod(): String
}
''')
        then:
        beanDefinition != null
        !beanDefinition.isAbstract()
        beanDefinition.injectedFields.size() == 0
        beanDefinition.executableMethods.size() == 1
        beanDefinition.executableMethods[0].hasAnnotation(Blocking)

        when:
        def context = ApplicationContext.run('foo.bar':'test')
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        instance.someMethod() == 'test'
    }

    void "test that a bean definition is not created for an abstract class"() {
        when:
        ApplicationContext ctx = buildContext('''
package test

import io.micronaut.aop.*
import io.micronaut.context.annotation.*
import io.micronaut.core.annotation.*
import io.micronaut.core.order.Ordered
import jakarta.inject.Singleton

interface ContractService {

    @SomeAnnot
    fun interfaceServiceMethod()
}

abstract class BaseService {

    @SomeAnnot
    open fun baseServiceMethod() {}
}

@SomeAnnot
abstract class BaseAnnotatedService

@Singleton
open class Service: BaseService(), ContractService {

    @SomeAnnot
    open fun serviceMethod() {}

    override fun interfaceServiceMethod() {}
}

@MustBeDocumented
@Retention
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.VALUE_PARAMETER)
@Around
@Type(SomeInterceptor::class)
annotation class SomeAnnot

@Singleton
class SomeInterceptor: MethodInterceptor<Any, Any>, Ordered {

    override fun intercept(context: MethodInvocationContext<Any, Any>): Any? {
        return context.proceed()
    }
}
''')
        then:
        Class clazz = ctx.classLoader.loadClass("test.ContractService")
        ctx.getBean(clazz)

        when:
        ctx.classLoader.loadClass("test.\$BaseService" + BeanDefinitionWriter.CLASS_SUFFIX)

        then:
        thrown(ClassNotFoundException)

        when:
        ctx.classLoader.loadClass("test.\$BaseService" + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX)

        then:
        thrown(ClassNotFoundException)

        when:
        ctx.classLoader.loadClass("test.\$BaseAnnotatedService" + BeanDefinitionWriter.CLASS_SUFFIX)

        then:
        thrown(ClassNotFoundException)
    }
}
