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
package io.micronaut.inject.annotation.internal;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Jakarta's priority annotation to Micronaut's order annotation.
 *
 * <p>The value is mapped across unchanged: {@code @Priority(10)} becomes {@code @Order(10)}. Both
 * annotations sort the lowest value first, which is the convention Jakarta Interceptors and Jakarta
 * RESTful Web Services use for the priorities they define, so the mapped order means the same thing
 * as the source annotation for those. {@code jakarta.annotation.Priority} itself leaves the
 * direction to the specification that consumes it, and a consumer following one that prefers the
 * highest value instead (the selection of a CDI alternative, for example) has to negate the
 * value it reads.</p>
 *
 * <p>An annotation mapper adds to what it reads rather than replacing it, so
 * {@code jakarta.annotation.Priority} is still present in the metadata after mapping and
 * {@code metadata.intValue("jakarta.annotation.Priority")} still returns the value as written. Code
 * that needs the priority exactly as the author declared it can read the source annotation directly
 * instead of going through {@link Order}.</p>
 *
 * @see Order
 */
@Internal
public final class JakartaPriorityAnnotationMapper implements NamedAnnotationMapper {

    private static final String SOURCE_ANNOTATION = "jakarta.annotation.Priority";

    @Override
    public String getName() {
        return SOURCE_ANNOTATION;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
            AnnotationValue.builder(Order.class)
                .value(annotation.intValue().orElse(0))
                .build()
        );
    }
}
