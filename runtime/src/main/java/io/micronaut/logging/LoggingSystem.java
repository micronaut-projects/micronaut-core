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
package io.micronaut.logging;

import io.micronaut.core.annotation.Indexed;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Abstraction for a logging system.
 *
 * @since 1.3.0
 * @author graemerocher
 * @author Denis Stepanov
 */
@Indexed(LoggingSystem.class)
public interface LoggingSystem {

    /**
     * Set the log level for the logger found by name (or created if not found).
     *
     * @param name the logger name
     * @param level the log level to set on the named logger
     */
    void setLogLevel(@NotBlank String name, @NotNull LogLevel level);

}
