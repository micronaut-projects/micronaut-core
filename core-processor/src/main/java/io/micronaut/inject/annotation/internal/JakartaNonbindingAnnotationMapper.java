/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.annotation.NonBinding;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps Jakarta CDI's {@code jakarta.enterprise.util.Nonbinding} to Micronaut's {@link NonBinding}.
 *
 * <p>A member of a qualifier or an interceptor binding marked with the Jakarta annotation is then recorded under
 * {@code $nonBinding} on every occurrence of the annotation, like one marked {@link NonBinding}, and is left out
 * when qualifiers and interceptor bindings are compared. The annotation is matched by name, so the CDI API does not
 * have to be on the processor path.</p>
 *
 * <p>Other processors may register their own mapper for the same annotation. All of them run, and since each one
 * adds the same {@link NonBinding}, a member is still recorded once.</p>
 *
 * @since 5.3.0
 */
@Internal
public final class JakartaNonbindingAnnotationMapper implements NamedAnnotationMapper {

    private static final String SOURCE_ANNOTATION = "jakarta.enterprise.util.Nonbinding";

    @Override
    public String getName() {
        return SOURCE_ANNOTATION;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return List.of(AnnotationValue.builder(NonBinding.class).build());
    }
}
