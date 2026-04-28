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
package io.micronaut.inject.beans.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.List;

/**
 * Normalizes {@link Introspected.Property} name members.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Internal
public final class IntrospectedPropertyTransformer implements TypedAnnotationTransformer<Introspected.Property> {

    private static final String MEMBER_NAME = "name";

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Introspected.Property> annotation, VisitorContext visitorContext) {
        String value = annotation.stringValue().orElse("");
        String name = annotation.stringValue(MEMBER_NAME).orElse("");
        if (value.isEmpty() && name.isEmpty()) {
            return List.of(annotation);
        }
        var builder = annotation.mutate();
        boolean changed = false;
        if (name.isEmpty()) {
            builder.member(MEMBER_NAME, value);
            changed = true;
        }
        if (value.isEmpty()) {
            builder.member(AnnotationMetadata.VALUE_MEMBER, name);
            changed = true;
        }
        if (!changed) {
            return List.of(annotation);
        }
        return List.of(builder.build());
    }

    @Override
    public Class<Introspected.Property> annotationType() {
        return Introspected.Property.class;
    }
}
