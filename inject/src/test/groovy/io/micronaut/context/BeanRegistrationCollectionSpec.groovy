package io.micronaut.context

import io.micronaut.context.annotation.Requires
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

class BeanRegistrationCollectionSpec extends Specification {

    void "test beanregistration identifiers are returned"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])

        when:
        def service = ctx.getBean(MyService)

        then:
        service.beans*.identifier*.name.sort() == ['first-bean', 'second-bean']
    }

    static interface BaseBean {
    }

    @Singleton
    @Named("first-bean")
    @Requires(property = "spec.name", value = "BeanRegistrationCollectionSpec")
    static class FirstBean implements BaseBean {
    }

    @Singleton
    @Named("second-bean")
    @Requires(property = "spec.name", value = "BeanRegistrationCollectionSpec")
    static class SecondBean implements BaseBean {
    }

    @Singleton
    @Requires(property = "spec.name", value = "BeanRegistrationCollectionSpec")
    static class MyService {

        final Collection<BeanRegistration<BaseBean>> beans

        MyService(Collection<BeanRegistration<BaseBean>> beans) {
            this.beans = beans
        }
    }
}
