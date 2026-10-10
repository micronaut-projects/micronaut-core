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
import org.graalvm.polyglot.Source;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The type checking requested for a compilation.
 *
 * @param mode            The mode
 * @param annotationNames The qualified names of the decorators accepted as the {@code TypeChecked} switch
 * @since 5.3.0
 */
@Experimental
public record TypeCheckConfiguration(TypeCheckMode mode, List<String> annotationNames) {

    /**
     * The decorator names accepted by default: the framework's annotation and the name the pyronaut
     * build tools use for it.
     */
    public static final List<String> DEFAULT_ANNOTATION_NAMES = List.of(
        "io.micronaut.context.python.annotation.TypeChecked",
        "pyronaut.build.TypeChecked"
    );

    /**
     * No checking at all.
     */
    public static final TypeCheckConfiguration OFF = new TypeCheckConfiguration(TypeCheckMode.OFF, DEFAULT_ANNOTATION_NAMES);

    public TypeCheckConfiguration {
        Objects.requireNonNull(mode, "mode");
        annotationNames = annotationNames == null || annotationNames.isEmpty() ? DEFAULT_ANNOTATION_NAMES : List.copyOf(annotationNames);
    }

    /**
     * @param mode The mode
     * @return A configuration of that mode accepting the default decorator names
     */
    public static TypeCheckConfiguration of(TypeCheckMode mode) {
        return new TypeCheckConfiguration(mode, DEFAULT_ANNOTATION_NAMES);
    }

    /**
     * Whether the checker has anything to do for the given sources: the mode checks by default, or
     * a source mentions one of the switch decorators, which can switch checking on under
     * {@link TypeCheckMode#OFF}. A compilation that checks nothing does not pay for the checker.
     *
     * @param sources The sources of the compilation
     * @return Whether the checker runs
     */
    public boolean isEnabledFor(Collection<Source> sources) {
        if (mode != TypeCheckMode.OFF) {
            return true;
        }
        for (Source source : sources) {
            CharSequence text = source.getCharacters();
            for (String name : annotationNames) {
                String simpleName = name.substring(name.lastIndexOf('.') + 1);
                if (text.toString().contains(simpleName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
