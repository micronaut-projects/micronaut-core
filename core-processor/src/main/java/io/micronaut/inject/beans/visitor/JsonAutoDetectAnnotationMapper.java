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
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.ast.JsonAutoDetectConfiguration;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps Jackson's JsonAutoDetect to internal bean property visibility configuration.
 *
 * @author Denis Stepanov
 * @since 5.0.0
 */
@Internal
public final class JsonAutoDetectAnnotationMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonAutoDetect";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return List.of(AnnotationValue.builder(JsonAutoDetectConfiguration.class)
            .member(
                JsonAutoDetectConfiguration.MEMBER_GETTER_VISIBILITY,
                visibility(annotation, JsonAutoDetectConfiguration.MEMBER_GETTER_VISIBILITY)
            )
            .member(
                JsonAutoDetectConfiguration.MEMBER_IS_GETTER_VISIBILITY,
                visibility(annotation, JsonAutoDetectConfiguration.MEMBER_IS_GETTER_VISIBILITY)
            )
            .member(
                JsonAutoDetectConfiguration.MEMBER_SETTER_VISIBILITY,
                visibility(annotation, JsonAutoDetectConfiguration.MEMBER_SETTER_VISIBILITY)
            )
            .member(
                JsonAutoDetectConfiguration.MEMBER_FIELD_VISIBILITY,
                visibility(annotation, JsonAutoDetectConfiguration.MEMBER_FIELD_VISIBILITY)
            )
            .build());
    }

    private static JsonAutoDetectConfiguration.Visibility visibility(AnnotationValue<Annotation> annotation, String member) {
        return annotation.enumValue(member, JsonAutoDetectConfiguration.Visibility.class)
            .orElse(JsonAutoDetectConfiguration.Visibility.DEFAULT);
    }
}
