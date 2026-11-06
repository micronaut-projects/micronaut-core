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
package io.micronaut.python.processing.staticcompile;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * How much of a compilation's Python code is compiled to Java in the generated stubs.
 *
 * <p>Static compilation assumes the whole application is compiled at once: it is not meant for
 * libraries, and a subclass defined outside the compilation (in another module or library, inside
 * a function, or at runtime) that overrides a compiled method is not seen by Java callers, which
 * run the compiled body of the base.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public enum StaticCompilationMode {
    /**
     * Nothing is compiled unless switched on by a {@code CompileStatic} decorator. The planner, and
     * the type checker it needs, only run when a source applies such a decorator; otherwise the
     * compilation pays nothing and no report is written.
     */
    OFF,
    /**
     * Only the scopes carrying a {@code CompileStatic} decorator are compiled: exactly the functions
     * {@link #OFF} compiles, with the same warnings for a switch that cannot be honoured. The
     * difference is the cost and the report: the planner and the type checker always run over every
     * source, and the report, when a directory is configured, is always written with a decision for
     * every function, {@code EXCLUDED} for those no switch reaches. Use it to audit a code base
     * without changing what is compiled.
     */
    ANNOTATED,
    /**
     * Every eligible function is compiled unless a decorator excludes it.
     */
    ALL;

    /**
     * The name of the annotation processor option carrying the mode: {@code off} (the default),
     * {@code annotated} or {@code all}. {@code off} and {@code annotated} compile the same functions,
     * see {@link #ANNOTATED} for what differs.
     */
    public static final String OPTION = "micronaut.python.compile.static";

    /**
     * The name of the annotation processor option naming the directory the report is written to.
     */
    public static final String REPORT_OPTION = "micronaut.python.compile.static.report";

    /**
     * The name of the annotation processor option that makes an explicit {@code CompileStatic}
     * that cannot be honoured an error instead of a warning.
     */
    public static final String STRICT_OPTION = "micronaut.python.compile.static.strict";

    /**
     * The name of the annotation processor option listing the qualified names of the decorators
     * accepted as the {@code CompileStatic} switch, comma separated.
     */
    public static final String ANNOTATIONS_OPTION = "micronaut.python.compile.static.annotations";

    /**
     * The name of the annotation processor option that makes every compiled body count its entries
     * in {@code PythonStatic.entries()}, for tests and diagnosis of what runs as Java.
     */
    public static final String TRACE_OPTION = "micronaut.python.compile.static.trace";

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
    public static StaticCompilationMode fromOption(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        for (StaticCompilationMode mode : values()) {
            if (mode.optionValue().equals(value.trim().toLowerCase(Locale.ROOT))) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown value [" + value + "] of the option " + OPTION + "; expected one of off, annotated, all");
    }
}
