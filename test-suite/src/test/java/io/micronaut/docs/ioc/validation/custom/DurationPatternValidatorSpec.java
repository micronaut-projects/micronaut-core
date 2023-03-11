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
package io.micronaut.docs.ioc.validation.custom;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class DurationPatternValidatorSpec {

    // tag::test[]
    @Inject HolidayService holidayService;

    @Test
    void testCustomValidator() {
        final ConstraintViolationException exception =
            assertThrows(ConstraintViolationException.class, () ->
                holidayService.startHoliday("Fred", "junk") // <1>
            );

        assertEquals("startHoliday.duration: invalid duration (junk), additional custom message", exception.getMessage()); // <2>
    }

    // Issue:: micronaut-core/issues/6519
    @Test
    void testCustomAndDefaultValidator() {
        final ConstraintViolationException exception =
                assertThrows(ConstraintViolationException.class, () ->
                        holidayService.startHoliday( "fromDurationJunk", "toDurationJunk", "")
                );

        String notBlankValidated = exception.getConstraintViolations().stream().filter(constraintViolation -> Objects.equals(constraintViolation.getPropertyPath().toString(), "startHoliday.person")).map(ConstraintViolation::getMessage).findFirst().get();
        String fromDurationPatternValidated = exception.getConstraintViolations().stream().filter(constraintViolation -> Objects.equals(constraintViolation.getPropertyPath().toString(), "startHoliday.fromDuration")).map(ConstraintViolation::getMessage).findFirst().get();
        String toDurationPatternValidated = exception.getConstraintViolations().stream().filter(constraintViolation -> Objects.equals(constraintViolation.getPropertyPath().toString(), "startHoliday.toDuration")).map(ConstraintViolation::getMessage).findFirst().get();
        assertEquals("must not be blank", notBlankValidated);
        assertEquals("invalid duration (fromDurationJunk), additional custom message", fromDurationPatternValidated);
        assertEquals("invalid duration (toDurationJunk), additional custom message", toDurationPatternValidated);
    }
    // end::test[]
}
