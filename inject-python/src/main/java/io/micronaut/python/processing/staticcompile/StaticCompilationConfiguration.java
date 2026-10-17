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
import org.graalvm.polyglot.Source;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The static compilation requested for a compilation: the mode, where the report goes, whether an
 * explicit switch that cannot be honoured fails the build, and the decorator names accepted as the
 * switch.
 *
 * @param mode            The mode
 * @param reportDirectory The directory the report is written to, or {@code null} for no report files
 * @param strict          Whether an explicit {@code CompileStatic} that cannot be honoured is an error
 * @param annotationNames The qualified names of the decorators accepted as the switch
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public record StaticCompilationConfiguration(StaticCompilationMode mode,
                                             @Nullable Path reportDirectory,
                                             boolean strict,
                                             List<String> annotationNames) {

    /**
     * The decorator names accepted by default: the framework's annotation and the name the pyronaut
     * build tools use for it.
     */
    public static final List<String> DEFAULT_ANNOTATION_NAMES = List.of(
        "io.micronaut.context.python.annotation.CompileStatic",
        "pyronaut.build.CompileStatic"
    );

    /**
     * No static compilation at all.
     */
    public static final StaticCompilationConfiguration OFF = new StaticCompilationConfiguration(StaticCompilationMode.OFF, null, false, DEFAULT_ANNOTATION_NAMES);

    public StaticCompilationConfiguration {
        Objects.requireNonNull(mode, "mode");
        annotationNames = annotationNames == null || annotationNames.isEmpty() ? DEFAULT_ANNOTATION_NAMES : List.copyOf(annotationNames);
    }

    /**
     * @param mode The mode
     * @return A configuration of that mode without report files, accepting the default decorator names
     */
    public static StaticCompilationConfiguration of(StaticCompilationMode mode) {
        return new StaticCompilationConfiguration(mode, null, false, DEFAULT_ANNOTATION_NAMES);
    }

    /**
     * Whether the planner has anything to do for the given sources: the mode compiles by default, or
     * a source mentions one of the switch decorators, which can switch compilation on under
     * {@link StaticCompilationMode#OFF}. A compilation that compiles nothing does not pay for the planner.
     *
     * @param sources The sources of the compilation
     * @return Whether the planner runs
     */
    public boolean isEnabledFor(Collection<Source> sources) {
        if (mode != StaticCompilationMode.OFF) {
            return true;
        }
        for (Source source : sources) {
            String text = source.getCharacters().toString();
            for (String name : annotationNames) {
                if (text.contains(name.substring(name.lastIndexOf('.') + 1))) {
                    return true;
                }
            }
        }
        return false;
    }
}
