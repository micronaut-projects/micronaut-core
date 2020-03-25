package io.micronaut.docs.ioc.validation.pojo;

// tag::imports[]
import io.micronaut.docs.ioc.validation.Person;
import io.micronaut.test.annotation.MicronautTest;
import io.micronaut.validation.validator.Validator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.inject.Inject;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Set;
// end::imports[]

@MicronautTest
class PersonServiceSpec {

    // tag::validator[]
    @Inject
    Validator validator;

    @Test
    void testThatPersonIsValidWithValidator() {
        Person person = new Person();
        person.setName("");
        person.setAge(10);

        final Set<ConstraintViolation<Person>> constraintViolations = validator.validate(person);  // <1>

        assertEquals(2, constraintViolations.size()); // <2>
    }
    // end::validator[]

    // tag::validate-service[]
    @Inject
    PersonService personService;

    @Test
    void testThatPersonIsValid() {
        Person person = new Person();
        person.setName("");
        person.setAge(10);

        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                        personService.sayHello(person) // <1>
                );

        assertEquals(2, exception.getConstraintViolations().size()); // <2>
    }
    // end::validate-service[]
}


