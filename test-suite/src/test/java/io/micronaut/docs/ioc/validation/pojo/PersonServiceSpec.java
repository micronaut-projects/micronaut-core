/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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


