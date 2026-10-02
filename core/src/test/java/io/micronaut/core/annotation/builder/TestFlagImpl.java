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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueProvider;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.ConversionUtils;
import io.micronaut.core.util.CollectionUtils;

import java.util.Map;
import java.util.Objects;

/**
 * What the annotation processor generates for {@link TestFlag}, written by hand.
 */
final class TestFlagImpl implements TestFlag, AnnotationValueProvider<TestFlag> {

    private final String value;
    private final int level;

    TestFlagImpl(Map<? extends CharSequence, ?> values, Map<CharSequence, Object> defaults, ConversionService conversionService) {
        this.value = ConversionUtils.toString(ConversionUtils.member(values, defaults, "value"), conversionService);
        this.level = ConversionUtils.toInt(ConversionUtils.member(values, defaults, "level"), conversionService);
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public Class<TestFlag> annotationType() {
        return TestFlag.class;
    }

    @Override
    public AnnotationValue<TestFlag> annotationValue() {
        Map<CharSequence, Object> members = CollectionUtils.newLinkedHashMap(2);
        members.put("value", value);
        members.put("level", level);
        return new AnnotationValue<>(TestFlag.class.getName(), members);
    }

    @Override
    public int hashCode() {
        return (127 * "value".hashCode() ^ Objects.hashCode(value)) + (127 * "level".hashCode() ^ Integer.hashCode(level));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TestFlag flag && Objects.equals(value, flag.value()) && level == flag.level();
    }
}
