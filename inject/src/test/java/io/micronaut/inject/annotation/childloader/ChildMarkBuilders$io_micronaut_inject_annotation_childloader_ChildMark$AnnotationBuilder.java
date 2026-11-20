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
package io.micronaut.inject.annotation.childloader;

import io.micronaut.core.annotation.AbstractAnnotationBuilder;
import io.micronaut.core.annotation.AnnotationConversionUtils;
import io.micronaut.core.convert.ConversionService;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * A builder of {@link ChildMark} registered only in a class loader below the one of the test: no service entry of
 * the test class path names it.
 */
public final class ChildMarkBuilders$io_micronaut_inject_annotation_childloader_ChildMark$AnnotationBuilder extends AbstractAnnotationBuilder<ChildMark> {

    public ChildMarkBuilders$io_micronaut_inject_annotation_childloader_ChildMark$AnnotationBuilder() {
        super(ChildMark.class, Map.of());
    }

    @Override
    protected ChildMark create(Map<? extends CharSequence, ?> values, Map<CharSequence, Object> defaults, ConversionService conversionService) {
        String value = AnnotationConversionUtils.toString(AnnotationConversionUtils.member(values, defaults, "value"), conversionService);
        return new ChildMark() {
            @Override
            public String value() {
                return value;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return ChildMark.class;
            }

            @Override
            public boolean equals(Object other) {
                return other instanceof ChildMark mark && value.equals(mark.value());
            }

            @Override
            public int hashCode() {
                return 127 * "value".hashCode() ^ value.hashCode();
            }
        };
    }

    @Override
    public Map<String, Object> toMap(ChildMark annotation) {
        return Map.of("value", annotation.value());
    }
}
