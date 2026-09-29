/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.annotation.processing.test;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.function.Consumer;

/**
 * A Java interface whose method parameters carry constraint annotations, the shape of
 * {@code TransactionalEmailSender} of the email module: a Python implementation inherits the
 * constraints and the validation processor makes it validated.
 *
 * @param <I> The request type
 */
public interface ConstrainedMessageSender<I> {

    /**
     * @param subject The subject
     * @param request The request customizer
     * @return The subject sent
     */
    @NotNull
    String send(@NotNull @NotBlank String subject, @NotNull Consumer<I> request);

    /**
     * @param subject The subject
     * @return The subject sent
     */
    default String send(@NotNull @NotBlank String subject) {
        return send(subject, request -> { });
    }

    /**
     * @param payload The payload
     * @return The payload
     */
    @Valid
    ConstrainedPayload deliver(@NotNull @Valid ConstrainedPayload payload);
}
