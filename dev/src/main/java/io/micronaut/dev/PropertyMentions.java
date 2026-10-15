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
package io.micronaut.dev;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.DefaultPropertyPlaceholderResolver;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.expressions.EvaluatedExpression;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.value.MapPropertyResolver;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Whether an injection point names a changed property, which a refresh cannot update in a singleton that
 * received it.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class PropertyMentions {

    /**
     * Only parses: placeholders are split into segments the way the environment splits them, never resolved.
     */
    private static final DefaultPropertyPlaceholderResolver PARSER =
        new DefaultPropertyPlaceholderResolver(new MapPropertyResolver(Map.of()), ConversionService.SHARED);

    private PropertyMentions() {
    }

    /**
     * Whether an injection point names a changed property: {@code @Property(name = "key")}, or a {@code @Value}
     * with a placeholder naming the key, as {@code ${key}}, {@code ${key:default}}, {@code ${other:key:default}}
     * or the inner placeholder of {@code ${other:${key}}}. An evaluated expression that reads the environment or
     * a bean may read any key, so it names every change.
     *
     * @param change The change
     * @param metadata The annotation metadata of the injection point
     * @return True if it names a changed key, any key when everything changed
     */
    static boolean mentions(ConfigurationChange change, AnnotationMetadata metadata) {
        // the raw values: a string value read through the metadata has its placeholders resolved already
        Object property = metadata.getValues(Property.class.getName()).get("name");
        if (property != null && change.touches(property.toString())) {
            return true;
        }
        Object raw = metadata.getValues(Value.class.getName()).get("value");
        String expression = null;
        if (raw instanceof EvaluatedExpression evaluated) {
            // the expression class generated for the bean, which gives the expression as it was written
            expression = evaluated.toString();
        } else if (raw instanceof EvaluatedExpressionReference reference) {
            // the reference the compiler holds before the expression class is generated
            Object value = reference.annotationValue();
            expression = value instanceof Object[] array ? Arrays.toString(array) : String.valueOf(value);
        }
        if (expression != null) {
            // which keys an expression reads through the environment or a bean is not known, so any change counts
            return expression.contains("env") || expression.contains("ctx");
        }
        return raw != null && placeholderMentions(change, raw.toString());
    }

    private static boolean placeholderMentions(ConfigurationChange change, String value) {
        // every placeholder start is parsed on its own, so an inner placeholder, which the outer one's
        // segment ends at, is found as well as the outer one
        int start = value.indexOf(DefaultPropertyPlaceholderResolver.PREFIX);
        while (start >= 0) {
            List<DefaultPropertyPlaceholderResolver.Segment> segments = PARSER.buildSegments(value.substring(start), false);
            // only the placeholder starting here: a later one is parsed from its own start
            if (!segments.isEmpty() && segments.getFirst() instanceof DefaultPropertyPlaceholderResolver.PlaceholderSegment placeholder) {
                for (String key : placeholder.getExpressions()) {
                    String trimmed = key.trim();
                    if (!trimmed.isEmpty() && !trimmed.contains(DefaultPropertyPlaceholderResolver.PREFIX) && change.touches(trimmed)) {
                        return true;
                    }
                }
            }
            start = value.indexOf(DefaultPropertyPlaceholderResolver.PREFIX, start + 1);
        }
        return false;
    }
}
