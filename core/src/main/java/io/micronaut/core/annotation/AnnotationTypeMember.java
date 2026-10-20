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
package io.micronaut.core.annotation;

import io.micronaut.core.naming.Named;
import io.micronaut.core.type.ArgumentCoercible;

import java.util.Optional;

/**
 * A member of an {@link AnnotationType}: its name, its type as an {@link io.micronaut.core.type.Argument}, the
 * annotations written on it and its default.
 *
 * <p>The type is the type of the accessor: a Kotlin class reference is a {@link Class}. The
 * {@link #getAnnotationMetadata() annotation metadata} is the one of the member, which the
 * {@link #asArgument() argument} carries too.</p>
 *
 * @param <T> The type of the member
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AnnotationTypeMember<T> extends Named, AnnotationMetadataProvider, ArgumentCoercible<T> {

    /**
     * @return The type of the member
     */
    default Class<T> getType() {
        return asArgument().getType();
    }

    /**
     * Whether the member declares a default. A member without one is required on every use of the annotation.
     *
     * @return The state of the default
     */
    DefaultState getDefaultState();

    /**
     * The default the member declares, in the form the annotation metadata records a value: a boxed primitive, a
     * {@link String}, the name of an enum constant, an {@link AnnotationClassValue} for a class, an
     * {@link AnnotationValue} for an annotation, or an array of those.
     *
     * @return The default, empty unless the {@link #getDefaultState() state} is {@link DefaultState#PRESENT}
     */
    Optional<Object> getDefaultValue();

    /**
     * Whether a member declares a default.
     */
    enum DefaultState {
        /**
         * The member declares a default, which {@link #getDefaultValue()} answers.
         */
        PRESENT,
        /**
         * The member declares no default: it is required.
         */
        ABSENT,
        /**
         * The processor could not tell: KSP only sees the defaults of an annotation class on a use of the
         * annotation, so an annotation that no element of the compilation is annotated with has defaults it cannot
         * read.
         */
        UNKNOWN
    }
}
