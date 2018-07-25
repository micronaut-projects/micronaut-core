/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.ConvertibleValues;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Adapts an {@link AnnotationValue} to the environment.
 *
 * @author graemerocher
 * @since 1.0
 * @param <A> The annotation type
 */
class EnvironmentAnnotationValue<A extends Annotation> extends AnnotationValue<A> {
    private final ConvertibleValues<Object> convertibleValues;

    /**
     * Default constructor.
     *
     * @param environment The environment
     * @param target The target
     */
    EnvironmentAnnotationValue(Environment environment, AnnotationValue target) {
        super(target.getAnnotationName(), target.getValues());
        this.convertibleValues = EnvironmentConvertibleValuesMap.of(
                environment,
                target.getValues()
        );
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return convertibleValues.get(name, conversionContext);
    }

    @Override
    public ConvertibleValues<Object> getConvertibleValues() {
        return convertibleValues;
    }
}
