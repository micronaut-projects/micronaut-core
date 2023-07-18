/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.beans.visitor;

import io.micronaut.aop.InterceptorBinding;
import io.micronaut.aop.InterceptorKind;
import io.micronaut.context.annotation.Mapper;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Handles the mapper annotation.
 */
public final class MapperAnnotationMapper implements TypedAnnotationMapper<Mapper> {

    private static final @NonNull AnnotationValue<InterceptorBinding> BINDING = AnnotationValue.builder(InterceptorBinding.class).value(Mapper.class).member("kind", InterceptorKind.INTRODUCTION).build();

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Mapper> annotation, VisitorContext visitorContext) {
        return List.of(AnnotationValue.builder(annotation)
            .stereotype(BINDING).build());
    }

    @Override
    public Class<Mapper> annotationType() {
        return Mapper.class;
    }
}
