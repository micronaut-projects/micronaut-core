package io.micronaut.kotlin.processing.aop.compile

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class AbstractClassIntroductionSpec extends Specification {

    void "test that a non-abstract method defined in class is not overridden by the introduction advise"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean {
    abstract fun isAbstract(): String

    fun nonAbstract(): String = "good"
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'

        cleanup:
        context.close()
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

interface Foo {
    fun nonAbstract(): String
}

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean: Foo {
    abstract fun isAbstract(): String

    override fun nonAbstract(): String = "good"
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'

        cleanup:
        context.close()
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise that also defines advice on the method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

interface Foo {
    @Stub
    fun nonAbstract(): String
}

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean: Foo {

    abstract fun isAbstract(): String

    override fun nonAbstract(): String = "good"
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise that also defines advice on a super interface method"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

interface Bar {
    @Stub
    fun nonAbstract(): String

    fun another(): String
}

interface Foo: Bar

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean: Foo {
    abstract fun isAbstract(): String

    override fun nonAbstract(): String = "good"

    override fun another(): String = "good"
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)


        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
        instance.another() == 'good'
    }

    void "test that a non-abstract method defined in class is and implemented from an interface not overridden by the introduction advise that also defines advice on the class"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

@Stub
interface Foo {
    fun nonAbstract(): String
}

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean: Foo {
    abstract fun isAbstract(): String

    override fun nonAbstract(): String = "good"
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
    }

    void "test that a default method defined in a interface is not implemented by Introduction advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

@Stub
interface Foo {
    fun nonAbstract(): String

    fun anotherNonAbstract(): String = "good"
}

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean: Foo {
    abstract fun isAbstract(): String

    override fun nonAbstract(): String = "good"
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
        instance.anotherNonAbstract() == 'good'
    }

    void "test that a default method overridden from parent interface is not implemented by Introduction advice"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AbstractBean' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.kotlin.processing.aop.introduction.Stub

interface Bar {
    fun anotherNonAbstract(): String
}

interface Foo: Bar {
    fun nonAbstract(): String

    override fun anotherNonAbstract(): String = "good"
}

@Stub
@jakarta.inject.Singleton
abstract class AbstractBean: Foo {
    abstract fun isAbstract(): String

    override fun nonAbstract(): String = "good"
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition)beanDefinition).instantiate(context)

        then:
        instance.isAbstract() == null
        instance.nonAbstract() == 'good'
        instance.anotherNonAbstract() == 'good'
    }
}
