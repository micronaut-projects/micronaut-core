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

import io.micronaut.context.env.exp.RandomPropertyExpressionResolver;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;

import java.util.ArrayList;
import java.util.Collection;
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
public class DefaultPropertyPlaceholderResolver implements PropertyPlaceholderResolver, AutoCloseable {

    /**
     * Prefix for placeholder in properties.
     */
    public static final String PREFIX = "${";

    /**
     * Suffix for placeholder in properties.
     */
    public static final String SUFFIX = "}";

    private static final Pattern ESCAPE_SEQUENCE = Pattern.compile("(.+)?:`([^`]+?)`");
    private static final List<PropertyExpressionResolver> DEFAULT_EXPRESSION_RESOLVERS = List.of(
        new RandomPropertyExpressionResolver()
    );
    private static final char COLON = ':';

    private final PropertyResolver environment;
    private final ConversionService conversionService;
    private final String prefix;
    private Collection<PropertyExpressionResolver> expressionResolvers;

    /**
     * @param environment The property resolver for the environment
     * @param conversionService The conversion service
     */
    public DefaultPropertyPlaceholderResolver(PropertyResolver environment, ConversionService conversionService) {
        this.environment = environment;
        this.conversionService = conversionService;
        this.prefix = PREFIX;
    }

    private Collection<PropertyExpressionResolver> getExpressionResolvers() {
        Collection<PropertyExpressionResolver> exResolvers = this.expressionResolvers;
        if (exResolvers == null) {
            synchronized (this) { // double check
                exResolvers = this.expressionResolvers;
                if (exResolvers == null) {
                    exResolvers = new ArrayList<>(DEFAULT_EXPRESSION_RESOLVERS);
                    ClassLoader classLoader = (environment instanceof Environment e) ? e.getClassLoader() : environment.getClass().getClassLoader();
                    SoftServiceLoader.load(PropertyExpressionResolver.class, classLoader).collectAll(exResolvers);
                    this.expressionResolvers = exResolvers;
                }
            }
        }
        return exResolvers;
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }

    @Override
    public Optional<String> resolvePlaceholders(String str) {
        List<Segment> segments = buildSegments(str, false);
        StringBuilder value = new StringBuilder();
        for (Segment segment: segments) {
            Optional<String> resolved = segment.findValue(String.class);
            if (resolved.isPresent()) {
                value.append(resolved.get());
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(value.toString());
    }

    @Override
    public String resolveRequiredPlaceholders(String str) throws ConfigurationException {
        List<Segment> segments = buildSegments(str);
        return resolveRequiredPlaceholdersString(segments);
    }

    @Override
    public Object resolveRequiredPlaceholdersObject(String str) throws ConfigurationException {
        List<Segment> segments = buildSegments(str);
        if (segments.size() == 1) {
            return segments.get(0).getValue(Object.class);
        }
        return resolveRequiredPlaceholdersString(segments);
    }

    private static String resolveRequiredPlaceholdersString(List<Segment> segments) {
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

    @Override
    public <T> Optional<T> resolveOptionalPlaceholder(String str, Class<T> type) throws ConfigurationException {
        List<Segment> segments = buildSegments(str, false);
        if (segments.size() == 1) {
            return segments.get(0).findValue(type);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Split a placeholder value into logic segments.
     *
     * @param str The placeholder
     * @return The list of segments
     */
    public List<Segment> buildSegments(String str) {
        return buildSegments(str, true);
    }

    /**
     * Split a placeholder value into logic segments.
     *
     * @param str The placeholder
     * @param failOnIncomplete True if should fail on incomplete placeholder, empty list will be returned otherwise
     * @return The list of segments
     * @since 4.2.0
     */
    private List<Segment> buildSegments(String str, boolean failOnIncomplete) {
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
                value = value.substring(suffixIdx + SUFFIX.length());
            } else if (failOnIncomplete) {
                throw new ConfigurationException("Incomplete placeholder definitions detected: " + str);
            } else {
                return List.of();
            }
            i = value.indexOf(PREFIX);
        }
        if (!value.isEmpty()) {
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
        for (PropertyExpressionResolver expressionResolver : getExpressionResolvers()) {
            Optional<T> value = expressionResolver.resolve(environment, conversionService, expression, type);
            if (value.isPresent()) {
                return value.get();
            }
        }
        if (environment.containsProperty(expression)) {
            return environment.getProperty(expression, type)
                    .orElseThrow(() ->
                            new ConfigurationException("Could not resolve expression: [" + expression + "] in placeholder ${" + context + "}"));
        }
        if (NameUtils.isEnvironmentName(expression)) {
            String envVar = CachedEnvironment.getenv(expression);
            if (StringUtils.isNotEmpty(envVar)) {
                return conversionService.convert(envVar, type)
                        .orElseThrow(() ->
                                new ConfigurationException("Could not resolve expression: [" + expression + "] in placeholder ${" + context + "}"));
            }
        }
        return null;
    }

    /**
     * Resolves a single optional expression.
     *
     * @param expression The expression
     * @param type The class
     * @param <T> The type the expression should be converted to
     * @return The resolved and converted expression
     */
    private <T> Optional<T> resolveOptionalExpression(String expression, Class<T> type) {
        for (PropertyExpressionResolver expressionResolver : getExpressionResolvers()) {
            Optional<T> value = expressionResolver.resolve(environment, conversionService, expression, type);
            if (value.isPresent()) {
                return value;
            }
        }
        Optional<T> property = environment.getProperty(expression, type);
        if (property.isPresent()) {
            return property;
        }
        if (NameUtils.isEnvironmentName(expression)) {
            String envVar = CachedEnvironment.getenv(expression);
            if (StringUtils.isNotEmpty(envVar)) {
                return conversionService.convert(envVar, type);
            }
        }
        return Optional.empty();
    }

    @Override
    public void close() throws Exception {
        if (expressionResolvers != null) {
            for (PropertyExpressionResolver expressionResolver : expressionResolvers) {
                if (expressionResolver instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            }
        }
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

        /**
         * Returns the optional value of a given segment converted to
         * the provided type. Any conversions errors are ignored.
         *
         * @param type The class
         * @param <T> The type to convert the value to
         * @return The converted optional value
         * @since 4.2.0
         */
       default <T> Optional<T> findValue(Class<T> type) {
           try {
               return Optional.of(getValue(type));
           } catch (ConfigurationException e) {
               return Optional.empty();
           }
       }
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

        @Override
        public <T> Optional<T> findValue(Class<T> type) {
            if (type.isInstance(text)) {
                return Optional.of((T) text);
            } else {
                return Optional.empty();
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
                                new ConfigurationException("Could not convert default value [%s] in placeholder ${%s}".formatted(defaultValue, placeholder)));
            } else {
                throw new ConfigurationException("Could not resolve placeholder ${" + placeholder + "}");
            }
        }

        @Override
        public <T> Optional<T> findValue(Class<T> type) {
            try {
                for (String expression: expressions) {
                    Optional<T> optionalValue = resolveOptionalExpression(expression, type);
                    if (optionalValue.isPresent()) {
                        return optionalValue;
                    }
                }
                if (defaultValue != null) {
                    return conversionService.convert(defaultValue, type);
                }
            } catch (ConfigurationException e) {
                // Swallow exception.
            }
            return Optional.empty();
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
