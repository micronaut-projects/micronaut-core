package io.micronaut.context

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.DisabledBeanException
import io.micronaut.context.exceptions.NoSuchBeanException
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

class DisabledBeanRequiresSpec extends Specification {

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11815")
    def "a bean that @Requires a bean disabled with DisabledBeanException is itself disabled"() {
        given:
        def ctx = ApplicationContext.run('spec.name': 'DisabledBeanRequiresSpec')

        expect: "the dependent bean is treated as disabled rather than throwing a wrapped exception"
        ctx.findBean(AnotherBean).isEmpty()

        cleanup:
        ctx.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/11815")
    def "a direct required lookup of the dependent bean still fails usefully"() {
        given:
        def ctx = ApplicationContext.run('spec.name': 'DisabledBeanRequiresSpec')

        when:
        ctx.getBean(AnotherBean)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        ctx.close()
    }

    static class MyBean {}

    static class AnotherBean {
        final MyBean myBean

        AnotherBean(MyBean myBean) {
            this.myBean = myBean
        }
    }

    @Factory
    @Requires(property = 'spec.name', value = 'DisabledBeanRequiresSpec')
    static class MyFactory {

        @Singleton
        MyBean myBean() {
            throw new DisabledBeanException("MyBean Disabled")
        }

        @Singleton
        @Requires(bean = MyBean)
        AnotherBean anotherBean(MyBean myBean) {
            return new AnotherBean(myBean)
        }
    }
}
