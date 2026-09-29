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
package docs.javainterfaces;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A Java interface with constrained method parameters, implemented by a Python class in the language guide.
 *
 * @param <M> The message type
 */
public interface MessageSender<M> {

    /**
     * Sends a message.
     *
     * @param recipient The recipient
     * @param message   The message
     * @return A receipt
     */
    String send(@NotBlank String recipient, @NotNull @Valid M message);

    /**
     * Sends a message to everyone.
     *
     * @param message The message
     * @return A receipt
     */
    default String broadcast(@NotNull @Valid M message) {
        return send("everyone", message);
    }
}
