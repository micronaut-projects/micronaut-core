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
package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.Pattern;

/**
 * Validator for the {@link Pattern} annotation.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class PatternValidator extends AbstractPatternValidator<Pattern> {

    @Override
    public boolean isValid(@Nullable CharSequence value, @NonNull AnnotationValue<Pattern> annotationMetadata, @NonNull ConstraintValidatorContext context) {
        if (value == null) {
            // null valid according to spec
            return true;
        }
        java.util.regex.Pattern regex = getPattern(annotationMetadata, false);
        return regex.matcher(value).matches();
    }
}
