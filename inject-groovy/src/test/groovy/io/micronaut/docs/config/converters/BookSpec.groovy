package io.micronaut.docs.config.converters

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import java.time.LocalDate

/**
 * @author Sergio del Amo
 * @since 1.0
 */
//tag::bookSpec[]
class BookSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext ctx = ApplicationContext.run([
            "myapp.books": [
                    [title: 'Daemon', author: 'Daniel Suarez'],
                    [title: 'Building Microservices', author: 'Sam Newman'],
            ], // <1>
            "myapp.updatedAt": [day: 28, month: 10, year: 1982]  // <2>
    ], "test")


    void "test book configuration properties are fetched"() {
        when:
        BookConfigurationProperties props = ctx.getBean(BookConfigurationProperties)

        then:
        props.books
        props.books.size() == 2
        props.books*.title == ['Daemon', 'Building Microservices']
        props.books*.author == ['Daniel Suarez', 'Sam Newman']

        props.updatedAt == LocalDate.of(1982, 10, 28)
    }
}
//end::bookSpec[]
