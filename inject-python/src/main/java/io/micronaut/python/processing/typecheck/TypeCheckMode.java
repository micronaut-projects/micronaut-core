/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.python.processing.typecheck;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How the type checker treats the Python sources of a compilation.
 *
 * <p>The mode is the default for every function; the {@code TypeChecked} decorator switches
 * checking on or off for a module, a class or a function, the nearest declaration winning. A
 * function switched on under {@link #OFF} is checked at {@link #ERROR} severity.</p>
 *
 * @since 5.3.0
 */
@Experimental
public enum TypeCheckMode {
    /**
     * Nothing is checked unless switched on by a {@code TypeChecked} decorator.
     */
    OFF,
    /**
     * Problems are reported as warnings.
     */
    WARN,
    /**
     * Problems are reported as errors and fail the compilation.
     */
    ERROR;

    /**
     * The name of the annotation processor option carrying the mode.
     */
    public static final String OPTION = "micronaut.python.typecheck";

    /**
     * The name of the annotation processor option listing the qualified names of the decorators
     * accepted as the {@code TypeChecked} switch, comma separated.
     */
    public static final String ANNOTATIONS_OPTION = "micronaut.python.typecheck.annotations";

    /**
     * @return The value of the option for this mode
     */
    public String optionValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param value The value of the option, or {@code null} when it is not set
     * @return The mode, {@link #OFF} when the option is not set
     * @throws IllegalArgumentException When the value names no mode
     */
    public static TypeCheckMode fromOption(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        for (TypeCheckMode mode : values()) {
            if (mode.optionValue().equals(value.trim().toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown value [" + value + "] of the option " + OPTION + "; expected one of off, warn, error");
    }
}
