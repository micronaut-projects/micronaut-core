package io.micronaut.inject.validation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import jakarta.validation.Valid
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BeanWithValidationSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(["spec.name": getClass().simpleName])

    void 'test bean definition is not created for a bean with validation'() {
        expect:
            context.getBeanDefinitions(Account1.class).isEmpty()
            context.getBeanDefinitions(Account2.class).isEmpty()
            context.getBeanDefinitions(Account3.class).isEmpty()
    }

    void 'test pojoCanHaveGetterWhichReturnsAnOptional'() {
        when:
            context.getBean(MockService).validate(new ListingArguments(0))
        then:
            noExceptionThrown()
    }

    @Requires(property = "spec.name", value = "BeanWithValidationSpec")
    @Singleton
    static class MockService {

        void validate(@Valid ListingArguments arguments) {

        }
    }
}
