package io.micronaut.docs.ioc.validation.pojo

import io.micronaut.docs.ioc.validation.Person

// tag::imports[]
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.validation.validator.Validator
import spock.lang.Specification

import jakarta.inject.Inject
import javax.validation.ConstraintViolationException
// end::imports[]

// tag::test[]
@MicronautTest
class PersonServiceSpec extends Specification {

    // tag::validator[]
    @Inject Validator validator

    void "test person is validated with validator"() {
        when:"The person is validated"
        def constraintViolations = validator.validate(new Person(name: "", age: 10)) // <1>

        then:"A validation error occurs"
        constraintViolations.size() == 2 //  <2>
    }
    // end::validator[]

    // tag::validate-service[]
    @Inject PersonService personService

    void "test person name is validated"() {
        when:"The sayHello method is called with an invalid person"
        personService.sayHello(new Person(name: "", age: 10)) // <1>

        then:"A validation error occurs"
        def e = thrown(ConstraintViolationException)
        e.constraintViolations.size() == 2 //  <2>
    }
    // end::validate-service[]
}
// end::test[]
