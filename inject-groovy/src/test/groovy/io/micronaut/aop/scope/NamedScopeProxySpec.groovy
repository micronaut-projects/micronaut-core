package io.micronaut.aop.scope

import groovy.transform.Canonical
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.*
import io.micronaut.runtime.context.scope.ScopedProxy
import spock.lang.Issue
import spock.lang.Specification

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME

class NamedScopeProxySpec extends Specification {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1484')
    void "test named scoped proxies are injected correctly"() {
        given:
        ApplicationContext context = ApplicationContext.run('some.named.config.default': 'primary','some.named.config.other': 'other')

        when:"We retrieve the beans"
        def beans = context.getBeansOfType(MyInterface)

        then:"The beans are proxies and are of the correct type"
        beans.size() == 2
        beans.every({ it instanceof Intercepted })
        beans.any { it.name == 'default' }
        beans.any { it.name == 'other' }

        cleanup:
        context.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/1484')
    void "test named scoped proxies are injected correctly into target bean"() {
        given:
        ApplicationContext context = ApplicationContext.run('some.named.config.default': 'primary','some.named.config.other': 'other')

        when:"We retrieve the beans"
        def myBean = context.getBean(MyBean)

        then:"The beans are proxies and are of the correct type"
        myBean.other.name == 'other'
        myBean.primary.name == 'default'

        cleanup:
        context.close()
    }


    @Factory
    static class MyFactory {

        @EachProperty(value = 'some.named.config', primary = 'default')
        MyNamedBean myBean(@Parameter String name) {
            return new MyNamedBean(name)
        }


        @EachBean(MyNamedBean)
        @MyScope
        MyInterface scopedProxy(MyNamedBean myBean) {
            return {-> myBean.name } as MyInterface
        }
    }

    @Singleton
    static class MyBean {
        @Inject
        @MyScope
        MyInterface primary

        @Inject
        @MyScope('other')
        MyInterface other
    }


    @Canonical
    static class MyNamedBean {
        String name
    }

    static interface MyInterface {
        String getName()
    }


}

@ScopedProxy
@Documented
@Retention(RUNTIME)
@Bean
@interface MyScope {

    /**
     * @return The name qualifier of the session bean
     */
    @AliasFor(annotation = Named.class, member = "value")
    String value() default "";
}
