/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Indexed;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import javax.validation.ClockProvider;
import javax.validation.Constraint;
import java.lang.annotation.Annotation;

/**
 * Constraint validator that can be used at either runtime or compilation time and
 * is capable of validation {@link javax.validation.Constraint} instances. Allows defining validators that work with both Hibernate validator and Micronaut's validator.
 *
 * <p>Unlike the specification's interface this one can uses as a functional interface. Implementor should not implement the {@link #initialize(Annotation)} method and should instead read the passed {@link AnnotationValue}.</p>
 *
 * @param <A> The annotation type
 * @param <T> The supported validation types
 */
@Immutable
@ThreadSafe
@Indexed(ConstraintValidator.class)
@FunctionalInterface
public interface ConstraintValidator<A extends Annotation, T> extends javax.validation.ConstraintValidator<A, T> {

    /**
     * A constraint validator that just returns the object as being valid.
     */
    ConstraintValidator VALID = (value, annotationMetadata, context) -> true;

    /**
     * Implements the validation logic.
     *
     * <p>Implementations should be thread-safe and immutable.</p>
     *
     * @param value object to validate
     * @param annotationMetadata The annotation metadata
     * @param context The context object
     *
     * @return {@code false} if {@code value} does not pass the constraint
     */
    boolean isValid(
            @Nullable T value,
            @NonNull AnnotationValue<A> annotationMetadata,
            @NonNull ConstraintValidatorContext context);

    @Override
    default boolean isValid(T value, javax.validation.ConstraintValidatorContext context) {
        // simply adapt the interfaces for now.
        return isValid(value, new AnnotationValue(Constraint.class.getName()), new ConstraintValidatorContext() {
            @NonNull
            @Override
            public ClockProvider getClockProvider() {
                return context.getClockProvider();
            }
        });
    }
}
