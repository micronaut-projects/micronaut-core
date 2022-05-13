package io.micronaut.aop.scope.qualified

import groovy.transform.Canonical
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.context.scope.ScopedProxy
import jakarta.inject.Inject
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import spock.lang.Specification

import java.lang.annotation.Documented
import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME

class QualifiedScopeProxySpec extends Specification {

    void "test qualified scoped proxies are injected correctly"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"We retrieve the beans"
        def beans = context.getBeansOfType(MyInterface)

        then:"The beans are proxies and are of the correct type"
        beans.size() == 2
        beans.every({ it instanceof Intercepted })
        beans.any { it.name == 'XYZ' }
        beans.any { it.name == 'ABC' }

        cleanup:
        context.close()
    }

    void "test qualified scoped proxies are injected correctly into target bean"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:"We retrieve the beans"
        def myBean = context.getBean(MyBean)

        then:"The beans are proxies and are of the correct type"
        myBean.bean2.name == 'ABC'
        myBean.bean1.name == 'XYZ'

        cleanup:
        context.close()
    }

    @Factory
    static class MyFactory {

        @MyQualifier1
        @MyScope
        MyNamedBean myBean1() {
            return new MyNamedBean("XYZ")
        }


        @MyQualifier2
        @MyScope
        MyInterface myBean2() {
            return new MyNamedBean("ABC")
        }
    }

    @Singleton
    static class MyBean {
        @Inject
        @MyQualifier1
        MyInterface bean1

        @Inject
        @MyQualifier2
        MyInterface bean2
    }


    @Canonical
    static class MyNamedBean implements MyInterface {
        String name
    }

    static interface MyInterface {
        String getName()
    }


}

@ScopedProxy
@Documented
@Retention(RUNTIME)
@interface MyScope {
}

@Qualifier
@Retention(RUNTIME)
@interface MyQualifier1 {}

@Qualifier
@Retention(RUNTIME)
@interface MyQualifier2 {}
