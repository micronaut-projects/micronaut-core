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
package io.micronaut.http.gateway.config;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The arguments of a predicate or a filter of a gateway route in the configuration: given by
 * name, {@code name} and {@code args}, or by position in the shortcut syntax, e.g.
 * {@code StripPrefix=2}, whose values take the names of the
 * {@link GatewayFilterFactory#shortcutFields() shortcut fields} of the factory.
 *
 * <p>The values are converted with the {@link ConversionService}. {@link #bind(Class)} binds
 * them to the constructor of an introspected type, e.g. a record, without reflection.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class GatewayArguments {

    private final String owner;
    private final Map<String, Object> values;
    private final ConversionService conversionService;

    /**
     * @param owner             The predicate or filter, for the error messages, e.g. {@code StripPrefix of the route orders}
     * @param values            The values by name
     * @param conversionService The conversion service
     */
    public GatewayArguments(String owner, Map<String, Object> values, ConversionService conversionService) {
        this.owner = owner;
        Map<String, Object> normalized = new LinkedHashMap<>();
        values.forEach((name, value) -> normalized.put(normalize(name), value));
        this.values = Collections.unmodifiableMap(normalized);
        this.conversionService = conversionService;
    }

    private static String normalize(String name) {
        return NameUtils.hyphenate(name.strip(), true);
    }

    /**
     * @return The values by name, hyphenated, e.g. {@code fallback-handler}
     */
    public Map<String, Object> getValues() {
        return values;
    }

    /**
     * @param name The name
     * @return The value as a string, if any
     */
    public Optional<String> find(String name) {
        Object value = values.get(normalize(name));
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof Collection<?> collection) {
            List<String> parts = new ArrayList<>(collection.size());
            for (Object item : collection) {
                parts.add(String.valueOf(item));
            }
            return Optional.of(String.join(",", parts));
        }
        return Optional.of(String.valueOf(value).strip());
    }

    /**
     * @param name The name
     * @return The value as a string
     * @throws ConfigurationException if it is missing
     */
    public String get(String name) {
        return find(name).orElseThrow(() -> new ConfigurationException("The argument " + name + " of " + owner + " is missing"));
    }

    /**
     * @param name         The name
     * @param type         The type
     * @param defaultValue The value if it is missing
     * @param <T>          The type
     * @return The value converted to the type, or the default
     * @throws ConfigurationException if it cannot be converted
     */
    public <T> T get(String name, Class<T> type, T defaultValue) {
        Object value = values.get(normalize(name));
        if (value == null) {
            return defaultValue;
        }
        return convert(name, value, Argument.of(type));
    }

    /**
     * @param name The name
     * @return The values of a list, given as a list or as a string with commas, e.g.
     * {@code GET,POST}
     */
    public List<String> getList(String name) {
        Object value = values.get(normalize(name));
        if (value == null) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                addSplit(list, String.valueOf(item));
            }
        } else {
            addSplit(list, String.valueOf(value));
        }
        return list;
    }

    private static void addSplit(List<String> list, String value) {
        for (String part : value.split(",")) {
            String stripped = part.strip();
            if (!stripped.isEmpty()) {
                list.add(stripped);
            }
        }
    }

    /**
     * Bind the arguments to the constructor of an introspected type: each argument of the
     * constructor takes the value of its name, converted to its type, or else the
     * {@link Bindable#defaultValue()} it declares, or else {@code null}, or {@code 0} or
     * {@code false} for a primitive.
     *
     * @param type The introspected type
     * @param <T>  The type
     * @return The instance
     * @throws ConfigurationException if a value cannot be converted, or an argument is unknown
     */
    public <T> T bind(Class<T> type) {
        BeanIntrospection<T> introspection = BeanIntrospection.getIntrospection(type);
        Argument<?>[] arguments = introspection.getConstructorArguments();
        Object[] instance = new Object[arguments.length];
        List<String> known = new ArrayList<>(arguments.length);
        for (int i = 0; i < arguments.length; i++) {
            Argument<?> argument = arguments[i];
            String name = normalize(argument.getName());
            known.add(name);
            Object value = values.get(name);
            if (value == null) {
                AnnotationMetadata metadata = argument.getAnnotationMetadata();
                value = metadata.stringValue(Bindable.class, "defaultValue").orElse(null);
            }
            if (value == null) {
                instance[i] = defaultOf(argument.getType());
            } else if (value instanceof String s && s.isEmpty() && !CharSequence.class.isAssignableFrom(argument.getType())) {
                instance[i] = defaultOf(argument.getType());
            } else {
                instance[i] = convert(argument.getName(), value, argument);
            }
        }
        for (String name : values.keySet()) {
            if (!known.contains(name)) {
                throw new ConfigurationException("Unknown argument " + name + " of " + owner + ", expected one of " + known);
            }
        }
        return introspection.instantiate(instance);
    }

    private static @Nullable Object defaultOf(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        return 0;
    }

    private <T> T convert(String name, Object value, Argument<T> type) {
        Object source = value;
        if (value instanceof String s && Collection.class.isAssignableFrom(type.getType())) {
            // a list given as a string with commas
            List<String> parts = new ArrayList<>();
            addSplit(parts, s);
            source = parts;
        }
        Object src = source;
        return conversionService.convert(src, type)
            .orElseThrow(() -> new ConfigurationException("The argument " + name + " of " + owner + " is not a " + type.getTypeName() + ": " + src));
    }

    @Override
    public String toString() {
        return owner + " " + values;
    }

    /**
     * @param owner             The owner, for the messages
     * @param fields            The names of the positional arguments; a single name starting
     *                          with {@code *} takes every argument as a list
     * @param args              The arguments of the shortcut, after the {@code =}
     * @param conversionService The conversion service
     * @return The arguments by name: the last field takes the rest of the arguments, commas
     * included, e.g. a regular expression
     */
    static GatewayArguments ofShortcut(String owner, List<String> fields, String args, ConversionService conversionService) {
        Objects.requireNonNull(fields, "fields");
        Map<String, Object> values = new LinkedHashMap<>();
        if (args.isBlank()) {
            return new GatewayArguments(owner, values, conversionService);
        }
        if (fields.size() == 1 && fields.get(0).startsWith("*")) {
            // every argument is a value of one list, e.g. Method=GET,POST
            List<String> list = new ArrayList<>();
            addSplit(list, args);
            values.put(fields.get(0).substring(1), list);
            return new GatewayArguments(owner, values, conversionService);
        }
        if (fields.isEmpty()) {
            throw new ConfigurationException(owner + " takes no arguments: " + args);
        }
        String[] parts = args.split(",", fields.size());
        for (int i = 0; i < parts.length; i++) {
            values.put(fields.get(i), parts[i].strip());
        }
        return new GatewayArguments(owner, values, conversionService);
    }
}
