/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.validation.validator.constraints;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.validation.ValidationException;
import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Interface for a class that is a registry of contraint validator.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ConstraintValidatorRegistry {

    /**
     * Finds a constraint validator for the given type and target type.
     * @param constraintType The annotation type of the constraint.
     * @param targetType The type being validated.
     * @param <A> The annotation type
     * @param <T> The target type
     * @return The validator
     */
    @NonNull
    <A extends Annotation, T> Optional<ConstraintValidator<A, T>> findConstraintValidator(
            @NonNull Class<A> constraintType,
            @NonNull Class<T> targetType);

    /**
     * Finds a constraint validator for the given type and target type.
     * @param constraintType The annotation type of the constraint.
     * @param targetType The type being validated.
     * @param <A> The annotation type
     * @param <T> The target type
     * @return The validator
     * @throws ValidationException if no validator is present
     */
    @NonNull
    default <A extends Annotation, T> ConstraintValidator<A, T> getConstraintValidator(
            @NonNull Class<A> constraintType,
            @NonNull Class<T> targetType) {
        return findConstraintValidator(constraintType, targetType)
                .orElseThrow(() -> new ValidationException("No constraint validator present able to validate constraint [" + constraintType + "] on type: " + targetType));
    }

}
