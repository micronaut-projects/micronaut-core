package io.micronaut.docs.aop.validation

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.validation.Validated
import javax.annotation.Nonnull
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import javax.inject.Singleton
import javax.validation.ConstraintViolationException
import javax.validation.constraints.NotNull

class ValidatedWithJavaxAnnoationNonNullSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run([
            'spec.name': 'nonnull'
    ], Environment.TEST)

    void "javax.annotation.NonNull does not fails validation"() {
        given:
        NonNullService nonNullService = applicationContext.getBean(NonNullService)

        when:"An invalid title is passed"
        String salutation = nonNullService.sayHello(null)

        then:
        noExceptionThrown()
        salutation == 'Hello'

        when:
        NonNullAndNotNullService nonNullNotNullService = applicationContext.getBean(NonNullAndNotNullService)
        nonNullNotNullService.sayHello(null)

        then:"A constraint violation occurred"
        def e = thrown(ConstraintViolationException)
        e.message == 'sayHello.name: must not be null'
    }

    @Requires(property = 'spec.name', value = 'nonnull')
    @Singleton
    @Validated
    static class NonNullService {

        String sayHello(@Nonnull String name) {
            name ? "Hello $name" : "Hello"
        }
    }

    @Requires(property = 'spec.name', value = 'nonnull')
    @Singleton
    @Validated
    static class NonNullAndNotNullService {

        String sayHello(@NotNull @Nonnull String name) {
            name ? "Hello $name" : "Hello"
        }
    }
}

