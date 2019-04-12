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

package io.micronaut.validation.validator;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotatedElementValidator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Default implementation of {@link AnnotatedElementValidator}. Used for discovery via
 * service loader and not for direct public consumption. Considered internal.
 *
 * @author graemerocher
 * @since 1.2
 */
@Internal
public class DefaultAnnotatedElementValidator implements AnnotatedElementValidator {

    private final DefaultValidator validator;

    /**
     * Default constructor.
     */
    public DefaultAnnotatedElementValidator() {
        this.validator = new DefaultValidator(new DefaultValidatorConfiguration());
    }

    @Nonnull
    @Override
    public Set<String> validatedAnnotatedElement(@Nonnull AnnotatedElement element, @Nullable Object value) {
        return validator.validatedAnnotatedElement(element, value);
    }
}
