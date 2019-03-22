package io.micronaut.validation.validator

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

class ValidatorSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()
    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test simple bean validation"() {
        given:
        Book b = new Book(title: "", pages: 50)
        def violations = validator.validate(b).sort { it.propertyPath.iterator().next().name }

        expect:
        violations.size() == 4
        violations[0].invalidValue == []
        violations[0].messageTemplate == '{javax.validation.constraints.Size.message}'
        violations[1].invalidValue == 50
        violations[1].propertyPath.iterator().next().name == 'pages'
        violations[1].rootBean == b
        violations[1].rootBeanClass == Book
        violations[1].messageTemplate == '{javax.validation.constraints.Min.message}'
        violations[2].invalidValue == null
        violations[2].propertyPath.iterator().next().name == 'primaryAuthor'
        violations[3].invalidValue == ''
        violations[3].propertyPath.iterator().next().name == 'title'

    }


}

@Introspected
class Book {
    @NotBlank
    String title

    @Min(100l)
    int pages

    @Valid
    @NotNull
    Author primaryAuthor

    @Size(min = 1, max = 10)
    @Valid
    List<Author> authors = []
}

@Introspected
class Author {
    @Max(100l)
    Integer age
}