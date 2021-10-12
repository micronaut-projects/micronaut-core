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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Transforms {@link jakarta.annotation.PreDestroy} into the javax meta-annotation {@link io.micronaut.core.annotation.AnnotationUtil#PRE_DESTROY}.
 *
 * @author graemerocher
 * @since 3.0
 */
@Internal
public final class JakartaPreDestroyTransformer implements NamedAnnotationTransformer {
    @NonNull
    @Override
    public String getName() {
        return "jakarta.annotation.PreDestroy";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(AnnotationUtil.PRE_DESTROY).build()
        );
    }
}
