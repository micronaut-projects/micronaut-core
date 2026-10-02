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
import io.micronaut.core.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Creates the instances of one annotation type.
 *
 * <p>A builder is stateless and thread safe, and is meant to be looked up once, with
 * {@link AnnotationBuilderRegistry#find(Class)}, and then reused. The implementations are generated at compile
 * time for the types listed in {@link AnnotationBuilders}.</p>
 *
 * <p>A member that is an annotation is read from an {@link AnnotationValue}, from a {@link Map} of its members or
 * from an instance, and an array of annotations from an array or a collection of those. A member that is a class
 * is read from a {@link Class}, an {@link AnnotationClassValue} or a class name, and an enum from a constant or
 * its name. Anything else is converted by the conversion service. A member that is not given gets the default
 * of the annotation type.</p>
 *
 * @param <A> The annotation type
 * @author Denis Stepanov
 * @since 5.3.0
 */
public interface AnnotationBuilder<A extends Annotation> {

    /**
     * @return The annotation type this builder creates
     */
    Class<A> annotationType();

    /**
     * Creates the annotation from the member values.
     *
     * @param values            The member values by member name
     * @param conversionService The conversion service converting the values to the member types
     * @return The annotation
     */
    A build(Map<? extends CharSequence, ?> values, ConversionService conversionService);

    /**
     * Creates the annotation from the member values, converted by the {@link ConversionService#SHARED shared}
     * conversion service.
     *
     * @param values The member values by member name
     * @return The annotation
     */
    default A build(Map<? extends CharSequence, ?> values) {
        return build(values, ConversionService.SHARED);
    }

    /**
     * Creates the annotation from an annotation value.
     *
     * @param annotationValue   The annotation value
     * @param conversionService The conversion service converting the values to the member types
     * @return The annotation
     */
    default A build(AnnotationValue<? extends Annotation> annotationValue, ConversionService conversionService) {
        // the members as the annotation value reads them, through its convertible values, which is where an
        // environment backed value resolves the placeholders
        Map<CharSequence, Object> stored = annotationValue.getValues();
        Map<CharSequence, Object> members = CollectionUtils.newLinkedHashMap(stored.size());
        for (Map.Entry<CharSequence, Object> member : stored.entrySet()) {
            String name = member.getKey().toString();
            members.put(name, annotationValue.getConvertibleValues().get(name, Object.class).orElse(member.getValue()));
        }
        return build(members, conversionService);
    }

    /**
     * Creates the annotation from an annotation value, converted by the {@link ConversionService#SHARED shared}
     * conversion service.
     *
     * @param annotationValue The annotation value
     * @return The annotation
     */
    default A build(AnnotationValue<? extends Annotation> annotationValue) {
        return build(annotationValue, ConversionService.SHARED);
    }
}
