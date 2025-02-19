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

import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Implementation of {@link ConfigurationPath}.
 *
 * @since 4.0.0
 */
final class DefaultConfigurationPath implements ConfigurationPath {
    private final LinkedList<ConfigurationSegment> list = new LinkedList<>();
    private String computedPrefix;
    private boolean hasDynamicSegments = false;
    private PropertyCatalog propertyCatalog = PropertyCatalog.NORMALIZED;

    DefaultConfigurationPath() {
        recomputeState();
    }

    @Override
    public boolean hasDynamicSegments() {
        return hasDynamicSegments ||
            kind() == ConfigurationSegment.ConfigurationKind.NAME ||
            kind() == ConfigurationSegment.ConfigurationKind.INDEX;
    }

    @Override
    public ConfigurationPath parent() {
        int i = list.size();
        if (i > 1) {
            DefaultConfigurationPath configurationPath = new DefaultConfigurationPath();
            configurationPath.list.addAll(list.subList(0, i - 1));
            configurationPath.hasDynamicSegments = hasDynamicSegments;
            configurationPath.recomputeState();
            return configurationPath;
        }
        return null;
    }

    @NonNull
    @Override
    public ConfigurationPath copy() {
        DefaultConfigurationPath newPath = new DefaultConfigurationPath();
        newPath.list.addAll(this.list);
        newPath.computedPrefix = computedPrefix;
        newPath.hasDynamicSegments = hasDynamicSegments;
        return newPath;
    }

    @NonNull
    @Override
    public String prefix() {
        return computedPrefix;
    }

    @NonNull
    @Override
    public String path() {
        ConfigurationSegment segment = peekLast();
        if (segment != null) {
            return segment.path();
        } else {
            return StringUtils.EMPTY_STRING;
        }
    }

    @Override
    public String primary() {
        ConfigurationSegment segment = peekLast();
        if (segment != null) {
            return segment.primary();
        }
        return null;
    }

    @Override
    public boolean isNotEmpty() {
        return !list.isEmpty();
    }

    @NonNull
    @Override
    public String resolveValue(String value) {
        return value.replace(path(), prefix());
    }

    @Override
    public Class<?> configurationType() {
        ConfigurationSegment segment = peekLast();
        if (segment != null) {
            return segment.type();
        }
        return null;
    }

    @Override
    public String name() {
        ConfigurationSegment segment = peekLast();
        if (segment != null) {
            return segment.name();
        }
        return null;
    }

    @Override
    public int index() {
        Iterator<ConfigurationSegment> i = list.descendingIterator();
        while (i.hasNext()) {
            ConfigurationSegment s = i.next();
            if (s.kind() == ConfigurationSegment.ConfigurationKind.INDEX) {
                return s.index();
            }
        }
        return -1;
    }

    @NonNull
    @Override
    public PropertyCatalog propertyCatalog() {
        return propertyCatalog;
    }

    @Override
    public String simpleName() {
        ConfigurationSegment segment = peekLast();
        if (segment != null) {
            return segment.simpleName();
        }
        return null;
    }

    @Override
    public void traverseResolvableSegments(@NonNull PropertyResolver propertyResolver, @NonNull Consumer<ConfigurationPath> callback) {
        if (hasDynamicSegments) {
            // match a path pattern like foo.*.bar.*
            Collection<List<String>> variableValues = propertyResolver.getPropertyPathMatches(path());
            for (List<String> variables : variableValues) {
                ConfigurationPath newPath = replaceVariables(variables);
                traversePath(newPath, propertyResolver, callback);
            }

        } else {
            // simple case just traverse entries
            traversePath(this, propertyResolver, callback);
        }
    }

    @SuppressWarnings("java:S1301")
    private ConfigurationPath replaceVariables(List<String> variables) {
        int varIndex = 0;
        DefaultConfigurationPath newPath = new DefaultConfigurationPath();
        newPath.hasDynamicSegments = true;
        for (ConfigurationSegment configurationSegment : list) {
            switch (configurationSegment.kind()) {
                case NAME, INDEX -> {
                    if (varIndex < variables.size()) {
                        ConfigurationSegment.ConfigurationKind kind = newPath.kind();
                        switch (kind) {
                            case LIST ->
                                newPath.pushConfigurationSegment(Integer.parseInt(variables.get(varIndex++)));
                            case MAP ->
                                newPath.pushConfigurationSegment(variables.get(varIndex++));
                            default ->
                                newPath.pushConfigurationSegment(configurationSegment);
                        }
                    } else {
                        newPath.pushConfigurationSegment(configurationSegment);
                    }
                }
                default -> newPath.pushConfigurationSegment(configurationSegment);
            }
        }

        return newPath;
    }

    private static void traversePath(ConfigurationPath thisPath, PropertyResolver propertyResolver, Consumer<ConfigurationPath> callback) {
        ConfigurationSegment.ConfigurationKind kind = thisPath.kind();
        switch (kind) {
            case MAP -> {
                Collection<String> entries = propertyResolver.getPropertyEntries(thisPath.prefix(), thisPath.propertyCatalog());
                for (String key : entries) {
                    ConfigurationPath newPath = thisPath.copy();
                    newPath.pushConfigurationSegment(key);
                    callback.accept(newPath);
                }
            }
            case LIST -> {
                List<?> entries = propertyResolver.getProperty(thisPath.prefix(), List.class, Collections.emptyList());
                for (int i = 0; i < entries.size(); i++) {
                    Object o = entries.get(i);
                    if (o != null) {
                        ConfigurationPath newPath = thisPath.copy();
                        newPath.pushConfigurationSegment(i);
                        callback.accept(newPath);
                    }
                }
            }
            case NAME, INDEX -> {
                ConfigurationPath parent = thisPath.parent();
                if (parent != null) {
                    traversePath(parent, propertyResolver, callback);
                }
            }
            default -> {
                if (propertyResolver.containsProperties(thisPath.prefix())) {
                    callback.accept(thisPath);
                }
            }
        }
    }

    @NonNull
    @Override
    public ConfigurationSegment.ConfigurationKind kind() {
        ConfigurationSegment segment = peekLast();
        if (segment != null) {
            return segment.kind();
        }
        return ConfigurationSegment.ConfigurationKind.ROOT;
    }

    @Override
    public ConfigurationSegment peekLast() {
        return list.peekLast();
    }

    @Override
    public boolean isWithin(String prefix) {
        return prefix != null && prefix.startsWith(path());
    }

    @Override
    public void pushEachPropertyRoot(@NonNull BeanDefinition<?> beanDefinition) {
        if (!beanDefinition.getBeanType().equals(configurationType())) {

            if (kind() != ConfigurationSegment.ConfigurationKind.ROOT) {
                this.hasDynamicSegments = true;
            }

            propertyCatalog = beanDefinition.enumValue(EachProperty.class, "catalog", PropertyCatalog.class).orElse(PropertyCatalog.NORMALIZED);

            boolean isList = beanDefinition.booleanValue(EachProperty.class, "list").orElse(false);
            String prefix = beanDefinition.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
            if (prefix != null) {
                String currentPath = path();
                if (!prefix.startsWith(currentPath)) {
                    throw new IllegalStateException("Invalid configuration properties nesting for path [" + prefix + "]. Expected: " + currentPath);
                }

                String resolvedPrefix = prefix;
                if (!currentPath.equals(StringUtils.EMPTY_STRING) && !prefix.equals(currentPath)) {
                    resolvedPrefix = prefix.substring(currentPath.length() + 1);
                }

                String property = resolvedPrefix.substring(0, resolvedPrefix.length() - (isList ? 3 : 2));
                String primaryName = beanDefinition.stringValue(EachProperty.class, "primary").orElse(null);

                list.add(new DefaultConfigurationSegment(
                    beanDefinition.getBeanType(),
                    property,
                    prefix,
                    isList ? ConfigurationSegment.ConfigurationKind.LIST : ConfigurationSegment.ConfigurationKind.MAP,
                    null,
                    null,
                    primaryName,
                    -1
                ));
                recomputeState();
            }
        }
    }

    @Override
    public void pushConfigurationReader(@NonNull BeanDefinition<?> beanDefinition) {
        if (!beanDefinition.getBeanType().equals(configurationType())) {

            if (kind() != ConfigurationSegment.ConfigurationKind.ROOT) {
                this.hasDynamicSegments = true;
            }

            String prefix = beanDefinition.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
            if (prefix != null) {
                String currentPath = path();
                if (!prefix.startsWith(currentPath)) {
                    throw new IllegalStateException("Invalid configuration properties nesting for path [" + prefix + "]. Expected: " + currentPath);
                }
                String p = prefix.substring(currentPath.length() + 1);
                list.add(new DefaultConfigurationSegment(
                    beanDefinition.getBeanType(),
                    p,
                    prefix,
                    ConfigurationSegment.ConfigurationKind.ROOT,
                    name(),
                    simpleName(),
                    primary(),
                    -1
                ));
                recomputeState();
            }
        }
    }

    @Override
    public void pushConfigurationSegment(@NonNull ConfigurationSegment configurationSegment) {
        ConfigurationSegment.ConfigurationKind kind = configurationSegment.kind();
        switch (kind) {
            case NAME ->
                pushConfigurationSegment(configurationSegment.name());
            case INDEX ->
                pushConfigurationSegment(configurationSegment.index());
            case ROOT ->
                list.add(new DefaultConfigurationSegment(
                    configurationSegment.type(),
                    configurationSegment.prefix(),
                    configurationSegment.path(),
                    ConfigurationSegment.ConfigurationKind.ROOT,
                    name(), // inherit name
                    simpleName(),
                    primary(),  // inherit name
                    index() // inherit the index
                ));
            default ->
                list.add(configurationSegment);
        }

        recomputeState();
    }

    @Override
    public void pushConfigurationSegment(@NonNull String name) {
        String primary = primary();
        ConfigurationSegment.ConfigurationKind kind = kind();
        String p = switch (kind) {
            case MAP -> path();
            case LIST ->
                throw new IllegalStateException("Illegal @EachProperty nesting encountered. Lists require numerical entries.");
            default ->
                throw new IllegalStateException("Illegal @EachProperty nesting, expecting a nested named not another configuration reader or name.");
        };
        String qualifiedName = computeName(name);
        list.add(new DefaultConfigurationSegment(
            configurationType(),
            name,
            p,
            ConfigurationSegment.ConfigurationKind.NAME,
            qualifiedName,
            name,
            primary,
            -1
        ));
        recomputeState();
    }

    @Override
    public void pushConfigurationSegment(int index) {
        ConfigurationSegment.ConfigurationKind kind = kind();
        String p = switch (kind) {
            case MAP ->
                throw new IllegalStateException("Illegal @EachProperty nesting encountered. Maps require key entries.");
            case LIST -> path();
            default ->
                throw new IllegalStateException("Illegal @EachProperty nesting, expecting a nested named not another configuration reader or name.");
        };
        String primary = primary();
        String strIndex = String.valueOf(index);
        String qualifiedName = computeName(strIndex);
        list.add(new DefaultConfigurationSegment(
            configurationType(),
            "["+ strIndex + "]",
            p,
            ConfigurationSegment.ConfigurationKind.INDEX,
            qualifiedName,
            strIndex,
            primary,
            index
        ));
        recomputeState();
    }

    private String computeName(String simpleName) {
        String qualifiedName = null;
        Iterator<ConfigurationSegment> i = list.descendingIterator();
        while (i.hasNext()) {
            qualifiedName = i.next().name();
            if (qualifiedName != null) {
                break;
            }
        }
        if (qualifiedName != null) {
            qualifiedName = qualifiedName + "-" + simpleName;
        } else {
            qualifiedName = simpleName;
        }
        return qualifiedName;
    }

    private void recomputeState() {
        StringBuilder str = new StringBuilder();
        Iterator<ConfigurationSegment> i = list.iterator();
        ConfigurationSegment previous = null;
        while (i.hasNext()) {
            ConfigurationSegment configurationSegment = i.next();
            if (configurationSegment.kind() == ConfigurationSegment.ConfigurationKind.INDEX) {
                str.append('[').append(configurationSegment.index()).append(']');
            } else {
                if (previous != null) {
                    str.append('.');
                }
                str.append(configurationSegment);
            }
            previous = configurationSegment;
        }
        computedPrefix = str.toString();
    }

    @NonNull
    @Override
    public ConfigurationSegment removeLast() {
        try {
            return list.removeLast();
        } finally {
            recomputeState();
        }
    }

    @Override
    public String toString() {
        return computedPrefix;
    }

    @NonNull
    @Override
    public Iterator<ConfigurationSegment> iterator() {
        return list.iterator();
    }

    record DefaultConfigurationSegment(
        Class<?> type,
        String prefix,
        String path,
        ConfigurationKind kind,
        String name,
        String simpleName,
        String primary,
        int index) implements ConfigurationSegment {

        @Override
        public int length() {
            return prefix.length();
        }

        @Override
        public char charAt(int index) {
            return prefix.charAt(index);
        }

        @NonNull
        @Override
        public CharSequence subSequence(int start, int end) {
            return prefix.subSequence(start, end);
        }

        @NonNull
        @Override
        public String toString() {
            return prefix;
        }
    }
}
