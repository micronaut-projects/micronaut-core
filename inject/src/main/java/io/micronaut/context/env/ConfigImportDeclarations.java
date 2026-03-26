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
import io.micronaut.core.util.ConnectionString;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ConfigImportDeclarations {

    static final String CONFIG_IMPORT = "micronaut.config.import";
    private static final Pattern INDEXED_PATTERN = Pattern.compile("^micronaut\\.config\\.import\\[(\\d+)]$");

    private ConfigImportDeclarations() {
    }

    static ParsedPropertySource normalize(PropertySource propertySource) {
        Object rootValue = null;
        boolean hasRoot = false;
        TreeMap<Integer, Object> indexedValues = new TreeMap<>();

        for (String key : propertySource) {
            if (CONFIG_IMPORT.equals(key)) {
                hasRoot = true;
                rootValue = propertySource.get(key);
                continue;
            }
            Matcher matcher = INDEXED_PATTERN.matcher(key);
            if (matcher.matches()) {
                int index = Integer.parseInt(matcher.group(1));
                indexedValues.put(index, propertySource.get(key));
            }
        }

        List<ConnectionString> declarations = new ArrayList<>();
        if (hasRoot && !indexedValues.isEmpty()) {
            throw new ConfigurationException("Cannot combine micronaut.config.import and indexed micronaut.config.import[n] declarations in " + propertySource.getName());
        }

        if (hasRoot) {
            declarations.addAll(parseRootDeclaration(rootValue, propertySource));
        } else if (!indexedValues.isEmpty()) {
            declarations.addAll(parseIndexedDeclarations(indexedValues, propertySource));
        }

        if (!hasRoot && indexedValues.isEmpty()) {
            return new ParsedPropertySource(propertySource, List.of());
        }

        Map<String, Object> cleanMap = new LinkedHashMap<>();
        for (String key : propertySource) {
            if (!CONFIG_IMPORT.equals(key) && !INDEXED_PATTERN.matcher(key).matches()) {
                cleanMap.put(key, propertySource.get(key));
            }
        }

        return new ParsedPropertySource(copyPropertySource(propertySource, cleanMap), List.copyOf(declarations));
    }

    private static List<ConnectionString> parseRootDeclaration(@Nullable Object value,
                                                               PropertySource propertySource) {
        if (value == null) {
            throw new ConfigurationException("micronaut.config.import cannot be null in " + propertySource.getName());
        }
        List<ConnectionString> declarations = new ArrayList<>();
        if (value instanceof CharSequence sequence) {
            declarations.add(ConnectionString.parse(sequence.toString()));
            return declarations;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof CharSequence sequence)) {
                    throw new ConfigurationException("micronaut.config.import list values must be strings in " + propertySource.getName());
                }
                declarations.add(ConnectionString.parse(sequence.toString()));
            }
            return declarations;
        }
        throw new ConfigurationException("micronaut.config.import must be a string or list in " + propertySource.getName());
    }

    private static List<ConnectionString> parseIndexedDeclarations(Map<Integer, Object> values, PropertySource propertySource) {
        List<ConnectionString> declarations = new ArrayList<>(values.size());
        int expectedIndex = 0;
        for (Map.Entry<Integer, Object> entry : values.entrySet()) {
            if (entry.getKey() != expectedIndex) {
                throw new ConfigurationException("micronaut.config.import indexed declarations must be contiguous from 0 in " + propertySource.getName());
            }
            Object value = entry.getValue();
            if (!(value instanceof CharSequence sequence)) {
                throw new ConfigurationException("micronaut.config.import[" + entry.getKey() + "] must be a string in " + propertySource.getName());
            }
            declarations.add(ConnectionString.parse(sequence.toString()));
            expectedIndex++;
        }
        return declarations;
    }

    private static PropertySource copyPropertySource(PropertySource original, Map<String, Object> values) {
        return new MapPropertySource(original.getName(), values) {
            @Override
            public int getOrder() {
                return original.getOrder();
            }

            @Override
            public PropertyConvention getConvention() {
                return original.getConvention();
            }

            @Override
            public Origin getOrigin() {
                return original.getOrigin();
            }
        };
    }

    record ParsedPropertySource(PropertySource propertySource, List<ConnectionString> imports) {
    }
}
