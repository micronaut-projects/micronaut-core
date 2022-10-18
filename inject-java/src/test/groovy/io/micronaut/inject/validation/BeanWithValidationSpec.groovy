package io.micronaut.inject.validation

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class BeanWithValidationSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void 'test bean definition is not created for a bean with validation'() {
        expect:
            context.getBeanDefinitions(Account1.class).isEmpty()
            context.getBeanDefinitions(Account2.class).isEmpty()
            context.getBeanDefinitions(Account3.class).isEmpty()
    }
}
