/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

public class ToTransformTransformer implements TypedAnnotationTransformer<ToTransform> {
    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<ToTransform> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder("test.Test").build()
        );
    }

    @Override
    public Class<ToTransform> annotationType() {
        return ToTransform.class;
    }
}
