/*
 * Copyright 2017-2021 original authors
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

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.util.Collections;
import java.util.List;

/**
 * A transformer that remaps {@link io.micronaut.core.annotation.Nullable} to {@link io.micronaut.core.annotation.AnnotationUtil#NULLABLE}.
 *
 * @author graemerocher
 * @since 2.4.0
 */
@Internal
public class CoreNullableTransformer implements NamedAnnotationTransformer {

    @NonNull
    @Override
    public String getName() {
        return "io.micronaut.core.annotation.Nullable";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder(AnnotationUtil.NULLABLE);
        annotation.booleanValue("inherited").ifPresent(b -> {
            if (Boolean.TRUE.equals(b)) {
                builder.stereotype(AnnotationValue.builder(Inherited.class).build());
            }
        });
        return Collections.singletonList(
            builder.build()
        );
    }
}

