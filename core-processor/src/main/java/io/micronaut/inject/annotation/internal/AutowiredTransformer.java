/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.inject.annotation.internal;

import io.micronaut.context.annotation.Autowired;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Transforms {@link Autowired} to {@link jakarta.inject.Inject}
 */
public class AutowiredTransformer
    implements TypedAnnotationTransformer<Autowired> {
    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Autowired> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder(AnnotationUtil.INJECT);
        annotation.booleanValue(Autowired.MEMBER_REQUIRED)
            .ifPresent(b -> builder.member(Autowired.MEMBER_REQUIRED, b));

        return List.of(
            builder
                .build()
        );
    }

    @Override
    public Class<Autowired> annotationType() {
        return Autowired.class;
    }
}
