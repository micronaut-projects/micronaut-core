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
package io.micronaut.core.annotation.builder;

import io.micronaut.core.annotation.AbstractAnnotationTypeBuilder;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationTypeMember;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

/**
 * What the annotation processor generates for {@link TestMarker}, written by hand: a builder that describes the
 * annotation type.
 */
public final class TestMarkerBuilders$io_micronaut_core_annotation_builder_TestMarker$AnnotationBuilder extends AbstractAnnotationTypeBuilder<TestMarker> {

    public TestMarkerBuilders$io_micronaut_core_annotation_builder_TestMarker$AnnotationBuilder() {
        super(TestMarker.class,
            Map.of("level", 2, "tags", new String[] {"a"}),
            AnnotationMetadata.EMPTY_METADATA,
            new AnnotationTypeMember<?>[] {
                new Member<>(Argument.of(String.class, "value"), AnnotationTypeMember.DefaultState.ABSENT, null),
                new Member<>(Argument.of(int.class, "level"), AnnotationTypeMember.DefaultState.PRESENT, 2),
                new Member<>(Argument.of(String[].class, "tags"), AnnotationTypeMember.DefaultState.PRESENT, new String[] {"a"})
            },
            TestMarkers.class.getName(),
            RetentionPolicy.RUNTIME,
            new ElementType[] {ElementType.TYPE, ElementType.METHOD},
            true);
    }

    @Override
    protected TestMarker create(Map<? extends CharSequence, ?> values, Map<CharSequence, Object> defaults, ConversionService conversionService) {
        throw new UnsupportedOperationException("Describes the annotation type only");
    }
}
