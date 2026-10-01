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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * Why a test failed.
 *
 * @param type The type of what was thrown, by name
 * @param message Its message, if any
 * @param stackTrace Its stack trace, as printed
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public record TestFailure(String type, @Nullable String message, String stackTrace) {

    /**
     * Validating constructor.
     *
     * @param type The type
     * @param message The message
     * @param stackTrace The stack trace
     */
    public TestFailure {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(stackTrace, "stackTrace");
    }

    /**
     * The failure a throwable describes.
     *
     * @param throwable What the test threw
     * @return The failure
     */
    public static TestFailure of(Throwable throwable) {
        StringWriter trace = new StringWriter();
        throwable.printStackTrace(new PrintWriter(trace));
        return new TestFailure(throwable.getClass().getName(), throwable.getMessage(), trace.toString());
    }
}
