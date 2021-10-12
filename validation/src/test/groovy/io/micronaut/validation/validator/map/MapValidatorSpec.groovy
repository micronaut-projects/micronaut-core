package io.micronaut.validation.validator.map

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.Validator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.Valid
import javax.validation.constraints.NotBlank

class MapValidatorSpec extends Specification {
    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()
    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test cascade validate to map"() {
        given:
        Author a = new Author(
                name: "Stephen King",
                books: [it:new Book(title: "")]
        )

        when:
        def constraintViolations = validator.validate(a)

        then:
        constraintViolations.size() == 1
        constraintViolations.first().propertyPath.toString() == 'books[it].title'
    }
}

@Introspected
class Author {
    String name

    @Valid
    Map<String, Book> books
}

@Introspected
class Book {
    @NotBlank
    String title
}
