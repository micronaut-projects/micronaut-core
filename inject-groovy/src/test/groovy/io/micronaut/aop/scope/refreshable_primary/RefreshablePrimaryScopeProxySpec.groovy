package io.micronaut.aop.scope.refreshable_primary

import groovy.transform.Canonical
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.core.annotation.AnnotationMetadata
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
            def myBean1 = context.getBean(MyNamedBean)
            def myBean2 = context.getBean(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary.class))
            def myBean3 = context.getBean(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary.class.getName()))
            def myBean4 = context.getBean(MyNamedBean, Qualifiers.byAnnotationSimple(AnnotationMetadata.EMPTY_METADATA, Primary.class.getName()))
            def myBean5 = context.getBean(MyNamedBean, Qualifiers.byStereotype(Primary.class))
            def myBean6 = context.getBean(MyNamedBean, Qualifiers.byStereotype(Primary.class.getName()))
            def allBeans = context.getBeansOfType(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary.class))

        then:
            myBean1.name == 'xyz1'
            myBean2.name == 'xyz1'
            myBean3.name == 'xyz1'
            myBean4.name == 'xyz1'
            myBean5.name == 'xyz1'
            myBean6.name == 'xyz1'
            allBeans.size() == 1
            allBeans[0].name == 'xyz1'

            context.getBean(MyFactory).created.intValue() == 1
        when:
            context.getBean(MyFactory).version.set(2)
            context.getBean(RefreshScope).refresh()
        then:
            myBean1.name == 'xyz2'
            myBean2.name == 'xyz2'
            myBean3.name == 'xyz2'
            myBean4.name == 'xyz2'
            myBean5.name == 'xyz2'
            myBean6.name == 'xyz2'
            allBeans.size() == 1
            allBeans[0].name == 'xyz2'
        when:
            myBean1 = context.getBean(MyNamedBean)
            myBean2 = context.getBean(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary.class))
            myBean3 = context.getBean(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary.class.getName()))
            myBean4 = context.getBean(MyNamedBean, Qualifiers.byAnnotationSimple(AnnotationMetadata.EMPTY_METADATA, Primary.class.getName()))
            myBean5 = context.getBean(MyNamedBean, Qualifiers.byStereotype(Primary.class))
            myBean6 = context.getBean(MyNamedBean, Qualifiers.byStereotype(Primary.class.getName()))
            allBeans = context.getBeansOfType(MyNamedBean, Qualifiers.byAnnotation(AnnotationMetadata.EMPTY_METADATA, Primary.class))
        then:
            myBean1.name == 'xyz2'
            myBean2.name == 'xyz2'
            myBean3.name == 'xyz2'
            myBean4.name == 'xyz2'
            myBean5.name == 'xyz2'
            myBean6.name == 'xyz2'
            allBeans.size() == 1
            allBeans[0].name == 'xyz2'
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


