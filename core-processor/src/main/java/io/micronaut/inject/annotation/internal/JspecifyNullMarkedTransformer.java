/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.NullMarked;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * A transformer that remaps org.jspecify.annotations.NullMarked to {@link NullMarked}.
 * @see <a href="https://jspecify.dev">JSpecify</a>
 * @since 4.10
 * @author Denis Stepanov
 */
@Internal
public final class JspecifyNullMarkedTransformer implements NamedAnnotationTransformer {

    @Override
    public @NonNull String getName() {
        return "org.jspecify.annotations.NullMarked";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
            AnnotationValue.builder(NullMarked.class).build()
        );
    }
}
