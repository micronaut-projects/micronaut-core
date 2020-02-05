/*
 * Copyright 2017-2019 original authors
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

package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotatedElement;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Set;

/**
 * Abstract validator for {@link AnnotatedElement} that may represent source code level validation routes
 * executed at compilation time.
 *
 * @since 1.2
 * @author graemerocher
 */
public interface AnnotatedElementValidator {

    /**
     * Validates an annotated element for the given value.
     * @param element The element
     * @param value  The value
     *
     * @return The error messages
     */
    @NonNull
    Set<String> validatedAnnotatedElement(
            @NonNull AnnotatedElement element,
            @Nullable Object value);
}
