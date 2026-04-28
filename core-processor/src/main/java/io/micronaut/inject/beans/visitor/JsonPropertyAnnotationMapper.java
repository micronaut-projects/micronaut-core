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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Makes every use of Jackson's JsonProperty also represent an {@link Introspected.Property}.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Internal
public final class JsonPropertyAnnotationMapper implements NamedAnnotationMapper {

    private static final String MEMBER_ACCESS = "access";

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonProperty";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        var builder = AnnotationValue.builder(Introspected.Property.class)
            .member("accessKind", mapAccess(annotation.stringValue(MEMBER_ACCESS).orElse("AUTO")));
        annotation.stringValue()
            .filter(name -> !name.isEmpty())
            .ifPresent(name -> builder.member("name", name));
        return List.of(builder.build());
    }

    private static Introspected.Property.Access[] mapAccess(String access) {
        return switch (access) {
            case "READ_ONLY" -> new Introspected.Property.Access[] {
                Introspected.Property.Access.READ
            };
            case "WRITE_ONLY" -> new Introspected.Property.Access[] {
                Introspected.Property.Access.WRITE
            };
            default -> new Introspected.Property.Access[] {
                Introspected.Property.Access.READ,
                Introspected.Property.Access.WRITE
            };
        };
    }
}
