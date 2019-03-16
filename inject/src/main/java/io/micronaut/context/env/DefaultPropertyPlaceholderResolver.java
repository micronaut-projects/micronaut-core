/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
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
     * @deprecated Use {@link #DefaultPropertyPlaceholderResolver(PropertyResolver, ConversionService)} instead
     */
    @Deprecated
    public DefaultPropertyPlaceholderResolver(PropertyResolver environment) {
        this(environment, ConversionService.SHARED);
    }

    /**
     * @param environment The property resolver for the environment
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
            return (T)segments.get(0).getValue(type);
        } else {
            throw new ConfigurationException("Cannot convert a multi segment placeholder to a specified type");
        }
    }

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
     * Resolves a replacement for the given expression. Returning true if the replacement was resolved.
     *
     * @deprecated No longer used. See {@link PlaceholderSegment#getValue(Class)}
     * @param builder The builder
     * @param str The full string
     * @param expr The current expression
     * @return True if a placeholder was resolved
     */
    @Deprecated
    protected boolean resolveReplacement(StringBuilder builder, String str, String expr) {
        if (environment.containsProperty(expr)) {
            builder.append(environment.getProperty(expr, String.class).orElseThrow(() -> new ConfigurationException("Could not resolve placeholder ${" + expr + "} in value: " + str)));
            return true;
        }
        return false;
    }

    public interface Segment<T> {

        T getValue(Class<T> type) throws ConfigurationException;
    }

    public class RawSegment<T> implements Segment<T> {

        private final String text;

        RawSegment(String text) {
            this.text = text;
        }

        @Override
        public T getValue(Class<T> type) throws ConfigurationException {
            return conversionService.convert(text, type)
                    .orElseThrow(() ->
                            new ConfigurationException("Could not convert: [" + text + "] to the required type: [" + type.getName() + "]"));
        }
    }

    public class PlaceholderSegment<T> implements Segment<T> {

        private final String placeholder;
        private final List<String> expressions = new ArrayList<>();
        private String defaultValue;

        PlaceholderSegment(String placeholder) {
            this.placeholder = placeholder;
            resolveExpression(placeholder);
        }

        public List<String> getExpressions() {
            return Collections.unmodifiableList(expressions);
        }

        private void resolveExpression(String placeholder) {
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
                    resolveExpression(defaultValue);
                } else {
                    this.defaultValue = defaultValue;
                }
            }
        }

        @Override
        public T getValue(Class<T> type) throws ConfigurationException {
            for (String expression: expressions) {
                if (environment.containsProperty(expression)) {
                    return environment.getProperty(expression, type)
                            .orElseThrow(() ->
                                    new ConfigurationException("Could not resolve expression: [" + expression + "] in placeholder ${" + placeholder + "}"));
                }
                if (NameUtils.isEnvironmentName(expression)) {
                    String envVar = System.getenv(expression);
                    if (StringUtils.isNotEmpty(envVar)) {
                        return conversionService.convert(envVar, type)
                                .orElseThrow(() ->
                                        new ConfigurationException("Could not resolve expression: [" + expression + "] in placeholder ${" + placeholder + "}"));
                    }
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
    }
}
