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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.annotation.Body;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;

import java.util.List;
import java.util.Map;

/**
 * The arguments a body is bound to, like a {@code @Body} parameter of a controller.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class BodyArguments {

    /**
     * The name of a body argument.
     */
    public static final String BODY_ARGUMENT = "body";

    /**
     * The metadata of a {@code @Body} parameter, which selects the body binder.
     */
    private static final AnnotationMetadata BODY = new DefaultAnnotationMetadata(
        Map.of(Body.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Body.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), List.of(Body.class.getName())),
        false
    );

    private static final AnnotationMetadata NULLABLE_BODY = new DefaultAnnotationMetadata(
        Map.of(Body.class.getName(), Map.of(), AnnotationUtil.NULLABLE, Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Bindable.class.getName(), Map.of()),
        Map.of(Body.class.getName(), Map.of(), AnnotationUtil.NULLABLE, Map.of()),
        Map.of(Bindable.class.getName(), List.of(Body.class.getName())),
        false
    );

    private BodyArguments() {
    }

    /**
     * The body argument of a type, bound like a {@code @Body} argument: annotated with the
     * annotations of the body type, e.g. a {@code @JsonView} the body is decoded with, and
     * {@code @Body}, and {@code @Nullable} if the type is nullable, like a {@code @Body}
     * parameter of a controller.
     *
     * @param bodyType The body type
     * @param <T>      The type
     * @return The argument
     */
    public static <T> Argument<T> bodyArgument(Argument<T> bodyType) {
        return Argument.of(bodyType.getType(), BODY_ARGUMENT, bodyMetadata(bodyType), bodyType.getTypeParameters());
    }

    private static AnnotationMetadata bodyMetadata(Argument<?> bodyType) {
        AnnotationMetadata body = bodyType.isNullable() ? NULLABLE_BODY : BODY;
        AnnotationMetadata annotations = bodyType.getAnnotationMetadata();
        if (annotations.isEmpty()) {
            return body;
        }
        // both are declared on the argument, as on a parameter of a controller
        return new AnnotationMetadataHierarchy(true, annotations, body);
    }
}
