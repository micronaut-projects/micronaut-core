/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;

import java.util.Map;
import java.util.Optional;

/**
 * Retrieve messages for the resolved locale.
 * @author Sergio del Amo
 * @since 3.4.0
 */
public interface LocalizedMessageSource {
    /**
     * Resolve a message for the given code.
     * @param code The code
     * @return A message if present
     */
    @NonNull Optional<String> getMessage(@NonNull String code);

    /**
     * Resolve a message for the given code and variables for the messages.
     * @param code The code
     * @param variables to be used to interpolate the message
     * @return A message if present
     */
    @NonNull Optional<String> getMessage(@NonNull String code, Object... variables);

    /**
     * Resolve a message for the given code and variables for the messages.
     * @param code The code
     * @param variables to be used to interpolate the message
     * @return A message if present
     */
    @NonNull Optional<String> getMessage(@NonNull String code, Map<String, Object> variables);

    /**
     * Resolve a message for the given code. If the message is not present then default message is returned.
     * @param code The code
     * @param defaultMessage The default message to use if no other message is found
     * @return A message if present. If the message is not present then default message supplied is returned.
     */
    default @NonNull String getMessageOrDefault(@NonNull String code, @NonNull String defaultMessage) {
        return getMessage(code).orElse(defaultMessage);
    }

    /**
     * Resolve a message for the given code. If the message is not present then default message is returned.
     * @param code The code
     * @param defaultMessage The default message to use if no other message is found
     * @param variables to be used to interpolate the message
     * @return A message if present. If the message is not present then default message supplied is returned.
     */
    default @NonNull String getMessageOrDefault(@NonNull String code, @NonNull String defaultMessage, Object... variables) {
        return getMessage(code, variables).orElse(defaultMessage);
    }

    /**
     * Resolve a message for the given code. If the message is not present then default message is returned.
     * @param code The code
     * @param defaultMessage The default message to use if no other message is found
     * @param variables to be used to interpolate the message
     * @return A message if present. If the message is not present then default message supplied is returned.
     */
    default @NonNull String getMessageOrDefault(@NonNull String code, @NonNull String defaultMessage, Map<String, Object> variables) {
        return getMessage(code, variables).orElse(defaultMessage);
    }
}
