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
package io.micronaut.validation.validator;

import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletionStage;

/**
 * Interface for reactive bean validation.
 *
 * @author graemerocher
 * @since 1.2
 */
public interface ReactiveValidator {

    /**
     * Validate the given publisher by returning a new Publisher that validates each emitted value. If a
     * constraint violation error occurs a {@link javax.validation.ConstraintViolationException} will be thrown.
     *
     * @param publisher The publisher
     * @param groups The groups
     * @param <T> The generic type
     * @return The publisher
     */
    @Nonnull <T> Publisher<T> validatePublisher(@Nonnull Publisher<T> publisher, Class<?>... groups);


    /**
     * Validate the given CompletionStage by returning a new CompletionStage that validates the emitted value. If a
     * constraint violation error occurs a {@link javax.validation.ConstraintViolationException} will be thrown.
     *
     * @param completionStage The completion stage
     * @param groups The groups
     * @param <T> The generic type
     * @return The publisher
     */
    @Nonnull <T> CompletionStage<T> validateCompletionStage(@Nonnull CompletionStage<T> completionStage, Class<?>... groups);
}
