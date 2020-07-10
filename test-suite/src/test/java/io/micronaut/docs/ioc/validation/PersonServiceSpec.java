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
