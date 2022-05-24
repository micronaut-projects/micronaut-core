package io.micronaut.aop.scope.refreshable_primary

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshScope
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class RefreshablePrimaryScopeProxySpec extends Specification {

    void "test refreshable beans beans retried by primary are the same"() {
        given:
            ApplicationContext context = ApplicationContext.run()
        when:
            def myBean1 = context.getBean(MyNamedBean)
        then:
            myBean1.name == 'xyz1'

            context.getBean(MyFactory).created.intValue() == 1
        when:
            context.getBean(MyFactory).version.set(2)
            context.getBean(RefreshScope).refresh()
        then:
            myBean1.name == 'xyz2'
        when:
            myBean1 = context.getBean(MyNamedBean)
        then:
            myBean1.name == 'xyz2'
            context.getBean(MyFactory).created.intValue() == 2

        cleanup:
            context.close()
    }

    @Factory
    static class MyFactory {

        AtomicInteger created = new AtomicInteger()
        AtomicInteger version = new AtomicInteger(1)

        @Refreshable
        MyNamedBean buildBean() {
            created.incrementAndGet()
            return new MyNamedBean("xyz" + version)
        }

    }

    @Canonical
    static class MyNamedBean implements MyInterface {
        String name
    }

    static interface MyInterface {
        String getName()
    }


}


