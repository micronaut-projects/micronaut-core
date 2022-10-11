package io.micronaut.aop.scope.refreshable_primary

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.qualifiers.PrimaryQualifier
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.Refreshable
import io.micronaut.runtime.context.scope.refresh.RefreshScope
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class RefreshablePrimaryScopeProxySpec extends Specification {

    void "test refreshable beans beans retried by primary are the same"() {
        given:
            ApplicationContext context = ApplicationContext.run()
        when:
            def myBean1 = context.getBean(MyUserBean)
            context.getBean(MyNamedBean, Qualifiers.byAnnotationSimple(AnnotationMetadata.EMPTY_METADATA, Primary.class.name)).name
            context.getBean(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary.class.name)).name
            context.getBean(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary)).name
            context.getBean(MyNamedBean, Qualifiers.byStereotype(Primary.class)).name
            context.getBean(MyNamedBean, PrimaryQualifier.INSTANCE).name
        then:
            myBean1.bean.name == 'xyz1'
            myBean1.bean.name == 'xyz1'

            context.getBean(MyFactory).created.intValue() == 1
        when:
            context.getBean(MyFactory).version.set(2)
            context.getBean(RefreshScope).refresh()
        then:
            myBean1.bean.name == 'xyz2'
        when:
            myBean1 = context.getBean(MyUserBean)
        then:
            myBean1.bean.name == 'xyz2'
            context.getBean(MyFactory).created.intValue() == 2

        cleanup:
            context.close()
    }

    @Factory
    static class MyFactory {

        AtomicInteger created = new AtomicInteger()
        AtomicInteger version = new AtomicInteger(1)

        @Primary
        @Refreshable
        MyNamedBean buildBean() {
            created.incrementAndGet()
            return new MyNamedBean("xyz" + version)
        }

        @EachBean(MyNamedBean.class)
        MyUserBean build(MyNamedBean bean) {
            return new MyUserBean(bean)
        }

    }

    static class MyUserBean {

        public final MyNamedBean bean

        MyUserBean(MyNamedBean bean) {
            this.bean = bean
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


