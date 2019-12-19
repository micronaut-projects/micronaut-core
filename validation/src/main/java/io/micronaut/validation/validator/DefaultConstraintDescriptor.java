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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import javax.validation.ConstraintTarget;
import javax.validation.ConstraintValidator;
import javax.validation.Payload;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.metadata.ValidateUnwrappedValue;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default constraint descriptor implementation.
 *
 * @param <T> The constraint type
 * @author graemerocher
 * @since 1.2
 */
@Internal
class DefaultConstraintDescriptor<T extends Annotation> implements ConstraintDescriptor<T> {

    private final AnnotationValue<T> annotationValue;
    private final AnnotationMetadata annotationMetadata;
    private final Class<T> type;

    /**
     * Default constructor.
     * @param annotationMetadata annotation metadata
     * @param type constraint type
     * @param annotationValue annotation value
     */
    DefaultConstraintDescriptor(
            AnnotationMetadata annotationMetadata,
            Class<T> type,
            AnnotationValue<T> annotationValue) {
        this.annotationValue = annotationValue;
        this.annotationMetadata = annotationMetadata;
        this.type = type;
    }

    @Override
    public T getAnnotation() {
        return annotationMetadata.synthesize(type);
    }

    @Override
    public String getMessageTemplate() {
        return annotationValue.get("groups", String.class).orElse(null);
    }

    @Override
    public Set<Class<?>> getGroups() {
        Set groups = annotationValue.get("groups", Argument.setOf(Class.class)).orElse(Collections.emptySet());
        //noinspection unchecked
        return groups;
    }

    @Override
    public Set<Class<? extends Payload>> getPayload() {
        Set payload = annotationValue.get("payload", Argument.setOf(Class.class)).orElse(Collections.emptySet());
        //noinspection unchecked
        return payload;
    }

    @Override
    public ConstraintTarget getValidationAppliesTo() {
        return ConstraintTarget.IMPLICIT;
    }

    @Override
    public List<Class<? extends ConstraintValidator<T, ?>>> getConstraintValidatorClasses() {
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return annotationValue.getValues().entrySet().stream().collect(Collectors.toMap(
                (entry) -> entry.getKey().toString(),
                Map.Entry::getValue
        ));
    }

    @Override
    public Set<ConstraintDescriptor<?>> getComposingConstraints() {
        return Collections.emptySet();
    }

    @Override
    public boolean isReportAsSingleViolation() {
        return false;
    }

    @Override
    public ValidateUnwrappedValue getValueUnwrapping() {
        return ValidateUnwrappedValue.DEFAULT;
    }

    @Override
    public Object unwrap(Class type) {
        throw new UnsupportedOperationException("Unwrapping unsupported");
    }
}
