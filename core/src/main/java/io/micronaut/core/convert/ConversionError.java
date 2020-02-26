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
package io.micronaut.core.convert;

import java.util.Optional;

/**
 * An interface for reporting conversion errors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConversionError {
    /**
     * @return The cause
     */
    Exception getCause();

    /**
     * @return The original rejected value
     */
    default Optional<Object> getOriginalValue() {
        return Optional.empty();
    }

}
