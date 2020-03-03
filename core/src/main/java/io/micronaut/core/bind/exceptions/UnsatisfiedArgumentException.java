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
package io.micronaut.core.bind.exceptions;

import io.micronaut.core.type.Argument;

/**
 * An exception thrown when an {@link io.micronaut.core.type.Argument} could not be satisfied
 * by a {@link io.micronaut.core.bind.ExecutableBinder}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class UnsatisfiedArgumentException extends RuntimeException {

    private final Argument<?> argument;

    /**
     * @param argument The {@link Argument}
     */
    public UnsatisfiedArgumentException(Argument<?> argument) {
        super(buildMessage(argument));
        this.argument = argument;
    }

    /**
     * @param argument The {@link Argument}
     * @param message The message
     */
    public UnsatisfiedArgumentException(Argument<?> argument, String message) {
        super("Argument [" + argument + "] not satisfied: " + message);
        this.argument = argument;

    }

    /**
     * @return The argument that could not be bound.
     */
    public Argument<?> getArgument() {
        return argument;
    }

    private static String buildMessage(Argument<?> argument) {
        return "Required argument [" + argument + "] not specified";
    }
}
