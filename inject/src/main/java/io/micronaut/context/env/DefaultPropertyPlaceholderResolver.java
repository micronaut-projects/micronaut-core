/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.context.env;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The default {@link PropertyPlaceholderResolver}.
 *
 * @author graemerocher
 * @since 1.0
 */
public class DefaultPropertyPlaceholderResolver implements PropertyPlaceholderResolver {

    /**
     * Prefix for placeholder in properties.
     */
    public static final String PREFIX = "${";

    /**
     * Suffix for placeholder in properties.
     */
    public static final String SUFFIX = "}";

    private static final Pattern ESCAPE_SEQUENCE = Pattern.compile("(.+)?:`([^`]+?)`");
    private static final char COLON = ':';

    private final PropertyResolver environment;
    private final ConversionService<?> conversionService;
    private final String prefix;

    /**
     * @param environment The property resolver for the environment
     * @param conversionService The conversion service
     */
    public DefaultPropertyPlaceholderResolver(PropertyResolver environment, ConversionService conversionService) {
        this.environment = environment;
        this.conversionService = conversionService;
        this.prefix = PREFIX;
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }

    @Override
    public Optional<String> resolvePlaceholders(String str) {
        try {
            return Optional.of(resolveRequiredPlaceholders(str));
        } catch (ConfigurationException e) {
            return Optional.empty();
        }
    }

    @Override
    public String resolveRequiredPlaceholders(String str) throws ConfigurationException {
        List<Segment> segments = buildSegments(str);
        StringBuilder value = new StringBuilder();
        for (Segment segment: segments) {
            value.append(segment.getValue(String.class));
        }
        return value.toString();
    }

    @Override
    public <T> T resolveRequiredPlaceholder(String str, Class<T> type) throws ConfigurationException {
        List<Segment> segments = buildSegments(str);
        if (segments.size() == 1) {
            return segments.get(0).getValue(type);
        } else {
            throw new ConfigurationException("Cannot convert a multi segment placeholder to a specified type");
        }
    }

    /**
     * Split a placeholder value into logic segments.
     *
     * @param str The placeholder
     * @return The list of segments
     */
    public List<Segment> buildSegments(String str) {
        List<Segment> segments = new ArrayList<>();
        String value = str;
        int i = value.indexOf(PREFIX);
        while (i > -1) {
            //the text before the prefix
            if (i > 0) {
                String rawSegment = value.substring(0, i);
                segments.add(new RawSegment(rawSegment));
            }
            //everything after the prefix
            value = value.substring(i + PREFIX.length());
            int suffixIdx = value.indexOf(SUFFIX);
            if (suffixIdx > -1) {
                String expr = value.substring(0, suffixIdx).trim();
                segments.add(new PlaceholderSegment(expr));
                if (value.length() > suffixIdx) {
                    value = value.substring(suffixIdx + SUFFIX.length());
                }
            } else {
                throw new ConfigurationException("Incomplete placeholder definitions detected: " + str);
            }
            i = value.indexOf(PREFIX);
        }
        if (value.length() > 0) {
            segments.add(new RawSegment(value));
        }
        return segments;
    }

    /**
     * Resolves a single expression.
     *
     * @param context The context of the expression
     * @param expression The expression
     * @param type The class
     * @param <T> The type the expression should be converted to
     * @return The resolved and converted expression
     */
    @Nullable
    protected <T> T resolveExpression(String context, String expression, Class<T> type) {
        if (environment.containsProperty(expression)) {
            return environment.getProperty(expression, type)
                    .orElseThrow(() ->
                            new ConfigurationException("Could not resolve expression: [" + expression + "] in placeholder ${" + context + "}"));
        }
        if (NameUtils.isEnvironmentName(expression)) {
            String envVar = System.getenv(expression);
            if (StringUtils.isNotEmpty(envVar)) {
                return conversionService.convert(envVar, type)
                        .orElseThrow(() ->
                                new ConfigurationException("Could not resolve expression: [" + expression + "] in placeholder ${" + context + "}"));
            }
        }
        return null;
    }

    /**
     * A segment of placeholder resolution.
     *
     * @author James Kleeh
     * @since 1.1.0
     */
    public interface Segment {

        /**
         * Returns the value of a given segment converted to
         * the provided type.
         *
         * @param type The class
         * @param <T> The type to convert the value to
         * @return The converted value
         * @throws ConfigurationException If any error occurs
         */
       <T> T getValue(Class<T> type) throws ConfigurationException;
    }

    /**
     * A segment that represents static text.
     *
     * @author James Kleeh
     * @since 1.1.0
     */
    public class RawSegment implements Segment {

        private final String text;

        /**
         * Default constructor.
         *
         * @param text The static text
         */
        RawSegment(String text) {
            this.text = text;
        }

        @Override
        public <T> T getValue(Class<T> type) throws ConfigurationException {
            if (type.isInstance(text)) {
                return (T) text;
            } else {
                return conversionService.convert(text, type)
                        .orElseThrow(() ->
                                new ConfigurationException("Could not convert: [" + text + "] to the required type: [" + type.getName() + "]"));
            }
        }
    }

    /**
     * A segment that represents one or more expressions
     * that should be searched for in the environment.
     *
     * @author James Kleeh
     * @since 1.1.0
     */
    public class PlaceholderSegment implements Segment {

        private final String placeholder;
        private final List<String> expressions = new ArrayList<>();
        private String defaultValue;

        /**
         * Default constructor.
         *
         * @param placeholder The placeholder value without
         *                    any prefix or suffix
         */
        PlaceholderSegment(String placeholder) {
            this.placeholder = placeholder;
            findExpressions(placeholder);
        }

        /**
         * @return The list of expressions that may be looked
         * up in the environment
         */
        public List<String> getExpressions() {
            return Collections.unmodifiableList(expressions);
        }

        @Override
        public <T> T getValue(Class<T> type) throws ConfigurationException {
            for (String expression: expressions) {
                T value = resolveExpression(placeholder, expression, type);
                if (value != null) {
                    return value;
                }
            }
            if (defaultValue != null) {
                return conversionService.convert(defaultValue, type)
                        .orElseThrow(() ->
                                new ConfigurationException(String.format("Could not convert default value [%s] in placeholder ${%s}", defaultValue, placeholder)));
            } else {
                throw new ConfigurationException("Could not resolve placeholder ${" + placeholder + "}");
            }

        }

        private void findExpressions(String placeholder) {
            String defaultValue = null;
            String expression;
            Matcher matcher = ESCAPE_SEQUENCE.matcher(placeholder);

            boolean escaped = false;
            if (matcher.find()) {
                defaultValue = matcher.group(2);
                expression = matcher.group(1);
                escaped = true;
            } else {
                int j = placeholder.indexOf(COLON);
                if (j > -1) {
                    defaultValue = placeholder.substring(j + 1);
                    expression = placeholder.substring(0, j);
                } else {
                    expression = placeholder;
                }
            }

            expressions.add(expression);

            if (defaultValue != null) {
                if (!escaped && (ESCAPE_SEQUENCE.matcher(defaultValue).find() || defaultValue.indexOf(COLON) > -1)) {
                    findExpressions(defaultValue);
                } else {
                    this.defaultValue = defaultValue;
                }
            }
        }
    }
}
