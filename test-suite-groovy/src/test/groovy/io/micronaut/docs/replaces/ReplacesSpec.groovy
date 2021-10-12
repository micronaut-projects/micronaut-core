package io.micronaut.docs.replaces

import io.micronaut.context.ApplicationContext
import io.micronaut.docs.requires.Book
import spock.lang.Specification

class ReplacesSpec extends Specification {

    void "test bean replaces"() {
        when:
        ApplicationContext applicationContext = ApplicationContext.run()

        then:
        applicationContext.getBean(BookService.class) instanceof MockBookService
        "An OK Novel" == applicationContext.getBean(Book.class).getTitle()
        "Learning 305" == applicationContext.getBean(TextBook.class).getTitle()

        cleanup:
        applicationContext.close()
    }
}
