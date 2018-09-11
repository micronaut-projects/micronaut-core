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

import java.lang.annotation.Annotation;

/**
 * Adapts an {@link AnnotationValue} to the environment.
 *
 * @author graemerocher
 * @since 1.0
 * @param <A> The annotation type
 */
class EnvironmentAnnotationValue<A extends Annotation> extends AnnotationValue<A> {

    /**
     * Default constructor.
     *
     * @param environment The environment
     * @param target The target
     */
    EnvironmentAnnotationValue(Environment environment, AnnotationValue<A> target) {
        super(target, AnnotationMetadataSupport.getDefaultValues(target.getAnnotationName()), EnvironmentConvertibleValuesMap.of(
                environment,
                target.getValues()
        ));
    }

}
