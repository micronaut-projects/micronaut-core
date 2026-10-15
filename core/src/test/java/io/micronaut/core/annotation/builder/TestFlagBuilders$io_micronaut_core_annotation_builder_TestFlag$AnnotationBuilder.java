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

import io.micronaut.core.annotation.AbstractAnnotationBuilder;
import io.micronaut.core.convert.ConversionService;

import java.util.Map;

/**
 * What the annotation processor generates for {@link TestFlag}, written by hand: the name carries the annotation
 * name, which is how the registry finds the builder without loading it.
 */
public final class TestFlagBuilders$io_micronaut_core_annotation_builder_TestFlag$AnnotationBuilder extends AbstractAnnotationBuilder<TestFlag> {

    public TestFlagBuilders$io_micronaut_core_annotation_builder_TestFlag$AnnotationBuilder() {
        super(TestFlag.class, Map.of("level", 1));
    }

    @Override
    protected TestFlag create(Map<? extends CharSequence, ?> values, Map<CharSequence, Object> defaults, ConversionService conversionService) {
        return new TestFlagImpl(values, defaults, conversionService);
    }
}
