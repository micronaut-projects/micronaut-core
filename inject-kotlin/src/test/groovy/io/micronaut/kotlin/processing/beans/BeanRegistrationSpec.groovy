package io.micronaut.kotlin.processing.beans

import io.micronaut.context.BeanRegistration
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class BeanRegistrationSpec extends Specification {

    void 'test inject bean registrations'() {
        given:
        def className = 'beanreg.Test'
        def context = buildContext( '''
package beanreg

import jakarta.inject.Singleton
import jakarta.inject.Inject
import jakarta.inject.Named
import io.micronaut.context.BeanRegistration

@Singleton
class Test(val registrations: Collection<BeanRegistration<Foo>>, val primaryBean: BeanRegistration<Foo>) {

    @Inject
    lateinit var fieldRegistrations: Collection<BeanRegistration<Foo>>

    @Inject
    lateinit var fieldArrayRegistrations: Array<BeanRegistration<Foo>>

    @Inject
    lateinit var methodRegistrations: List<BeanRegistration<Foo>>

    @Named("two")
    @Inject
    lateinit var secondaryBean: BeanRegistration<Foo>
}

interface Foo

@Singleton
@io.micronaut.context.annotation.Primary
class Foo1: Foo

@Singleton
@Named("two")
class Foo2: Foo
''')

        def bean = getBean(context, className)

        Collection<BeanRegistration> registrations = bean.registrations
        Collection<BeanRegistration> fieldRegistrations = bean.fieldRegistrations
        Collection<BeanRegistration> methodRegistrations = bean.methodRegistrations
        Collection<BeanRegistration> fieldArrayRegistrations = bean.fieldArrayRegistrations.toList()

        expect:
        bean.primaryBean.bean.getClass().name == 'beanreg.Foo1'
        bean.secondaryBean.bean.getClass().name == 'beanreg.Foo2'
        registrations.size() == 2
        fieldRegistrations.size() == 2
        fieldRegistrations == registrations
        fieldRegistrations as List == methodRegistrations
        fieldRegistrations as List == fieldArrayRegistrations
        registrations.any { it.bean.getClass().name == 'beanreg.Foo1'}
        registrations.any { it.bean.getClass().name == 'beanreg.Foo2'}

        cleanup:
        context.close()
    }

    void 'test inject bean registration for suspend lambda bean from factory'() {
        given:
        def className = 'beanreg.SuspendConsumer'
        def context = buildContext('''
package beanreg

import io.micronaut.context.BeanRegistration
import io.micronaut.context.annotation.Factory
import jakarta.inject.Singleton

interface Bar

@Singleton
class SuspendConsumer(val registration: BeanRegistration<suspend (Bar) -> Unit>)

@Factory
class SuspendFactory {
    @Singleton
    fun barLambda(): suspend (Bar) -> Unit = { _ -> }
}
''')

        when:
        def bean = getBean(context, className)

        then:
        bean.registration != null
        bean.registration.bean != null

        cleanup:
        context.close()
    }
}
