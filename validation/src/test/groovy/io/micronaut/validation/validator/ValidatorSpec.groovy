package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

class ValidatorSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run()

    void "test simple bean validation"() {
        given:
        Validator validator = applicationContext.getBean(Validator)

        Book b = new Book(title: "", pages: 50)
        def violations = validator.validate(b).sort { it.propertyPath.iterator().next().name }

        expect:
        violations.size() == 2
        violations[0].invalidValue == 50
        violations[0].propertyPath.iterator().next().name == 'pages'
        violations[0].rootBean == b
        violations[0].rootBeanClass == Book
        violations[0].messageTemplate == '{javax.validation.constraints.Min.message}'
        violations[1].invalidValue == ''
        violations[1].propertyPath.iterator().next().name == 'title'
    }


}

@Introspected
class Book {
    @NotBlank
    String title

    @Min(100l)
    int pages
}