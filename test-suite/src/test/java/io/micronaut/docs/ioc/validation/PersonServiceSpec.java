package io.micronaut.docs.ioc.validation;

// tag::imports[]
import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import javax.inject.Inject;
import javax.validation.ConstraintViolationException;
// end::imports[]
// tag::test[]
@MicronautTest
class PersonServiceSpec {

    @Inject PersonService personService;

    @Test
    void testThatNameIsValidated() {
        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                personService.sayHello("") // <1>
        );

        assertEquals("sayHello.name: must not be blank", exception.getMessage()); // <2>
    }
}
// end::test[]
