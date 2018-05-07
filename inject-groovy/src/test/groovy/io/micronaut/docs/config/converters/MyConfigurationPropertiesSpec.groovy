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
class MyConfigurationPropertiesSpec extends Specification {

    @AutoCleanup
    @Shared
    //tag::runContext[]
    ApplicationContext ctx = ApplicationContext.run(
            "myapp.updatedAt": [day: 28, month: 10, year: 1982]  // <1>
    )
    //end::runContext[]


    void "test convert date from map"() {
        when:
        MyConfigurationProperties props = ctx.getBean(MyConfigurationProperties)

        then:
        props.updatedAt == LocalDate.of(1982, 10, 28)
    }
}
//end::bookSpec[]
