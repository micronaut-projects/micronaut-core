package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class IntroductionWithAroundSpec extends Specification {

    void "test that around advice is applied to introduction concrete methods"() {
        when:"An introduction advice type is compiled that includes a concrete method that is annotated with around advice"
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.kotlin.processing.aop.introduction.Stub
import io.micronaut.kotlin.processing.aop.simple.Mutating
import jakarta.validation.constraints.*
import jakarta.inject.Singleton

@Stub
@Singleton
abstract class MyBean {
    abstract fun save(@NotBlank name: String, @Min(1L) age: Int)
    abstract fun saveTwo(@Min(1L) name: String)

    @Mutating("name")
    open fun myConcrete(name: String): String {
        return name
    }
}

''')

        then:"The around advice is applied to the concrete method"
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)

        then:
        instance.myConcrete("test") == 'changed'

        cleanup:
        context.close()
    }
}
