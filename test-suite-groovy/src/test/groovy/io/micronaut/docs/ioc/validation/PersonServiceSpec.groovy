package io.micronaut.docs.ioc.validation

// tag::imports[]
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.Specification

import jakarta.inject.Inject
import javax.validation.ConstraintViolationException
// end::imports[]

// tag::test[]
@MicronautTest
class PersonServiceSpec extends Specification {

    @Inject PersonService personService

    void "test person name is validated"() {
        when:"The sayHello method is called with a blank string"
        personService.sayHello("") // <1>

        then:"A validation error occurs"
        def e = thrown(ConstraintViolationException)
        e.message == "sayHello.name: must not be blank" //  <2>
    }
}
// end::test[]
