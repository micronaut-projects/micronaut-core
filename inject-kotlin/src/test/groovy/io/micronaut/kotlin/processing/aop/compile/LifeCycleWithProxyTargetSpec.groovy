package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class LifeCycleWithProxyTargetSpec extends Specification {

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at class level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.proxytarget.Mutating
import io.micronaut.core.convert.ConversionService

@Mutating("someVal")
@jakarta.inject.Singleton
open class MyBean {

    @jakarta.inject.Inject
    lateinit var conversionService: ConversionService

    var count = 0

    open fun someMethod(): String {
        return "good"
    }

    @jakarta.annotation.PostConstruct
    fun created() {
        count++
    }

    @jakarta.annotation.PreDestroy
    fun destroyed() {
        count--
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.postConstructMethods.size() == 1
        beanDefinition.preDestroyMethods.size() == 1

        when:
        def context = ApplicationContext.builder(beanDefinition.class.classLoader).start()
        def instance = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:"proxy post construct methods are not invoked"
        instance.conversionService // injection works
        instance.someMethod() == 'good'
        instance.count == 0

        and:"proxy target post construct methods are invoked"
        instance.interceptedTarget().count == 1

        cleanup:
        context.close()
    }

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at method level with hooks last"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.kotlin.processing.aop.proxytarget.Mutating
import io.micronaut.core.convert.ConversionService

@jakarta.inject.Singleton
open class MyBean {

    @jakarta.inject.Inject
    lateinit var conversionService: ConversionService

    var count = 0

    @Mutating("someVal")
    open fun someMethod(): String {
        return "good"
    }

    @jakarta.annotation.PostConstruct
    fun created() {
        count++
    }

    @jakarta.annotation.PreDestroy
    fun destroyed() {
        count--
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.postConstructMethods.size() == 1
        beanDefinition.preDestroyMethods.size() == 1

    }

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at method level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.kotlin.processing.aop.proxytarget.Mutating
import io.micronaut.core.convert.ConversionService

@jakarta.inject.Singleton
open class MyBean {

    @jakarta.inject.Inject
    lateinit var conversionService: ConversionService

    var count = 0

    @jakarta.annotation.PostConstruct
    fun created() {
        count++
    }

    @jakarta.annotation.PreDestroy
    fun destroyed() {
        count--
    }

    @Mutating("someVal")
    open fun someMethod(): String {
        return "good"
    }

}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
    }
}

