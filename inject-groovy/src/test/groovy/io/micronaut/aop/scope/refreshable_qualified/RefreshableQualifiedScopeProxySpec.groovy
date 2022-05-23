package io.micronaut.aop.scope.refreshable_qualified

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshScope
import jakarta.inject.Qualifier
import spock.lang.Specification

import java.lang.annotation.Retention
import java.util.concurrent.atomic.AtomicInteger

import static java.lang.annotation.RetentionPolicy.RUNTIME

class RefreshableQualifiedScopeProxySpec extends Specification {

    void "test refreshable beans with qualifiers should be correctly refreshed"() {
        given:
            ApplicationContext context = ApplicationContext.run(['beans.xyz': [:],
                                                                 'beans.abc': [:]])
        when:
            def myBean1 = context.getBean(MyNamedBeanService, Qualifiers.byName("xyz"))
            def myBean2 = context.getBean(MyNamedBeanService, Qualifiers.byName("abc"))

        then:
            myBean1.bean.name == 'xyz1'
            myBean2.bean.name == 'abc1'
            myBean1.bean.name == 'xyz1'
            myBean2.bean.name == 'abc1'

        when:
            context.getBean(MyFactory).version.set(2)
            context.getBean(RefreshScope).refresh()
        then:
            myBean1.bean.name == 'xyz2'
            myBean2.bean.name == 'abc2'
            myBean1.bean.name == 'xyz2'
            myBean2.bean.name == 'abc2'
            context.getBean(MyFactory).created.intValue() == 4

        cleanup:
            context.close()
    }

    @Factory
    static class MyFactory {

        AtomicInteger created = new AtomicInteger()
        AtomicInteger version = new AtomicInteger(1)

        @EachProperty("beans")
        MyBeanConf buildBeanConf(@Parameter String name) {
            return new MyBeanConf(name)
        }

        @Refreshable
        @EachBean(MyBeanConf)
        MyNamedBean buildBean(MyBeanConf conf) {
            created.incrementAndGet()
            return new MyNamedBean(conf.name + version)
        }

    }

    @EachBean(MyNamedBean)
    static class MyNamedBeanService {

        final MyNamedBean bean

        MyNamedBeanService(MyNamedBean bean) {
            this.bean = bean
        }

    }

    @Canonical
    static class MyBeanConf {
        String name
    }

    @Canonical
    static class MyNamedBean implements MyInterface {
        String name
    }

    static interface MyInterface {
        String getName()
    }


}

@Qualifier
@Retention(RUNTIME)
@interface MyQualifier1 {}

@Qualifier
@Retention(RUNTIME)
@interface MyQualifier2 {}
