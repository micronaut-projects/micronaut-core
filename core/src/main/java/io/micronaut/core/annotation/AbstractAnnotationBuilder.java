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
package io.micronaut.core.annotation;

import io.micronaut.core.convert.ConversionService;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * The base class of the generated {@link AnnotationBuilder}s: the generated class says what the annotation type
 * looks like and how to instantiate its implementation, this class resolves and converts the members.
 *
 * @param <A> The annotation type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@UsedByGeneratedCode
public abstract class AbstractAnnotationBuilder<A extends Annotation> implements AnnotationBuilder<A> {

    private final Class<A> type;
    private final Map<CharSequence, Object> defaults;

    /**
     * @param type     The annotation type
     * @param defaults The defaults by member name in the form the annotation metadata records them
     */
    protected AbstractAnnotationBuilder(Class<A> type, Map<CharSequence, Object> defaults) {
        this.type = type;
        this.defaults = defaults;
    }

    /**
     * Creates the implementation of the annotation, which reads and converts its members in its constructor.
     *
     * @param values            The values given
     * @param defaults          The defaults of the annotation type
     * @param conversionService The conversion service
     * @return The annotation
     */
    protected abstract A create(Map<? extends CharSequence, ?> values,
                                Map<CharSequence, Object> defaults,
                                ConversionService conversionService);

    @Override
    public final Class<A> annotationType() {
        return type;
    }

    @Override
    public final A build(Map<? extends CharSequence, ?> values, ConversionService conversionService) {
        try {
            return create(values, defaults, conversionService);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot build the annotation [" + type.getName() + "]: " + e.getMessage(), e);
        }
    }
}
