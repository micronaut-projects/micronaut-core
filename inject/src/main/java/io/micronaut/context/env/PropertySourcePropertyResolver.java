/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.MapPropertyResolver;
import io.micronaut.core.value.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A {@link PropertyResolver} that resolves from one or many {@link PropertySource} instances.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertySourcePropertyResolver implements PropertyResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PropertySourcePropertyResolver.class);
    private static final Pattern RANDOM_PATTERN = Pattern.compile("\\$\\{\\s?random\\.(\\S+?)\\}");

    protected final ConversionService<?> conversionService;
    protected final PropertyPlaceholderResolver propertyPlaceholderResolver;
    protected final Map<String, PropertySource> propertySources = new ConcurrentHashMap<>(10);
    // properties are stored in an array of maps organized by character in the alphabet
    // this allows optimization of searches by prefix
    @SuppressWarnings("MagicNumber")
    protected final Map<String, Object>[] catalog = new Map[57];
    private final Random random = new Random();
    private final Map<String, Boolean> containsCache = new ConcurrentHashMap<>(20);
    private final Map<String, Optional<?>> resolvedValueCache = new ConcurrentHashMap<>(20);

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver} for the given {@link ConversionService}.
     *
     * @param conversionService The {@link ConversionService}
     */
    public PropertySourcePropertyResolver(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
        this.propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(this);
    }

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver}.
     */
    public PropertySourcePropertyResolver() {
        this(ConversionService.SHARED);
    }

    /**
     * Creates a new {@link PropertySourcePropertyResolver} for the given {@link PropertySource} instances.
     *
     * @param propertySources The {@link PropertySource} instances
     */
    public PropertySourcePropertyResolver(PropertySource... propertySources) {
        this(ConversionService.SHARED);
        if (propertySources != null) {
            for (PropertySource propertySource : propertySources) {
                addPropertySource(propertySource);
            }
        }
    }

    /**
     * Add a {@link PropertySource} to this resolver.
     *
     * @param propertySource The {@link PropertySource} to add
     * @return This {@link PropertySourcePropertyResolver}
     */
    public PropertySourcePropertyResolver addPropertySource(@Nullable PropertySource propertySource) {
        if (propertySource != null) {
            propertySources.put(propertySource.getName(), propertySource);
            processPropertySource(propertySource, propertySource.getConvention());
        }
        return this;
    }

    /**
     * Add a property source for the given map.
     *
     * @param name   The name of the property source
     * @param values The values
     * @return This environment
     */
    public PropertySourcePropertyResolver addPropertySource(String name, @Nullable Map<String, ? super Object> values) {
        if (CollectionUtils.isNotEmpty(values)) {
            return addPropertySource(PropertySource.of(name, values));
        }
        return this;
    }

    @Override
    public boolean containsProperty(@Nullable String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        } else {
            Boolean result = containsCache.get(name);
            if (result == null) {
                Map<String, Object> entries = resolveEntriesForKey(name, false);
                if (entries == null) {
                    result = false;
                } else {
                    name = trimIndex(name);
                    result = entries.containsKey(name) || entries.containsKey(normalizeName(name));
                }
                containsCache.put(name, result);
            }
            return result;
        }
    }

    @Override
    public boolean containsProperties(@Nullable String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        } else {
            Map<String, Object> entries = resolveEntriesForKey(name, false);
            if (entries == null) {
                return false;
            } else {
                name = trimIndex(name);
                if (entries.containsKey(name) || entries.containsKey(normalizeName(name))) {
                    return true;
                } else {
                    String finalName = name + ".";
                    return entries.keySet().stream().anyMatch(key ->
                            key.startsWith(finalName)
                    );
                }
            }
        }
    }

    @Override
    public <T> Optional<T> getProperty(@Nullable String name, ArgumentConversionContext<T> conversionContext) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        } else {
            Class<T> requiredType = conversionContext.getArgument().getType();
            boolean cacheableType = requiredType == Boolean.class || requiredType == String.class;
            if (cacheableType && resolvedValueCache.containsKey(name)) {
                return (Optional<T>) resolvedValueCache.get(name);
            } else {
                Map<String, Object> entries = resolveEntriesForKey(name, false);
                if (entries != null) {
                    Object value = entries.get(name);
                    if (value == null) {
                        value = entries.get(normalizeName(name));
                    }
                    if (value == null) {
                        int i = name.indexOf('[');
                        if (i > -1 && name.endsWith("]")) {
                            String newKey = name.substring(0, i);
                            value = entries.get(newKey);
                            if (value != null) {
                                String index = name.substring(i + 1, name.length() - 1);
                                if (StringUtils.isNotEmpty(index)) {
                                    if (value instanceof List) {
                                        try {
                                            value = ((List) value).get(Integer.valueOf(index));
                                        } catch (NumberFormatException e) {
                                            // ignore
                                        }
                                    } else if (value instanceof Map) {
                                        try {
                                            value = ((Map) value).get(index);
                                        } catch (NumberFormatException e) {
                                            // ignore
                                        }
                                    }
                                }
                            } else {
                                String index = name.substring(i + 1, name.length() - 1);
                                if (StringUtils.isNotEmpty(index)) {
                                    String subKey = newKey + '.' + index;
                                    value = entries.get(subKey);
                                }
                            }
                        }
                    }

                    if (value != null) {
                        value = resolvePlaceHoldersIfNecessary(value);
                        Optional<T> converted = conversionService.convert(value, conversionContext);
                        if (LOG.isTraceEnabled()) {
                            if (converted.isPresent()) {
                                LOG.trace("Resolved value [{}] for property: {}", converted.get(), name);
                            } else {
                                LOG.trace("Resolved value [{}] cannot be converted to type [{}] for property: {}", value, conversionContext.getArgument(), name);
                            }
                        }

                        if (cacheableType) {
                            resolvedValueCache.put(name, converted);
                        }
                        return converted;
                    } else if (cacheableType) {
                        Optional<?> e = Optional.empty();
                        resolvedValueCache.put(name, e);
                        return (Optional<T>) e;
                    } else if (Properties.class.isAssignableFrom(requiredType)) {
                        Properties properties = resolveSubProperties(name, entries, conversionContext);
                        return Optional.of((T) properties);
                    } else if (Map.class.isAssignableFrom(requiredType)) {
                        Map<String, Object> subMap = resolveSubMap(name, entries, conversionContext);
                        return conversionService.convert(subMap, requiredType, conversionContext);
                    } else if (PropertyResolver.class.isAssignableFrom(requiredType)) {
                        Map<String, Object> subMap = resolveSubMap(name, entries, conversionContext);
                        return Optional.of((T) new MapPropertyResolver(subMap, conversionService));
                    }
                }
            }

        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("No value found for property: {}", name);
        }

        Class<T> requiredType = conversionContext.getArgument().getType();
        if (Properties.class.isAssignableFrom(requiredType)) {
            return Optional.of((T) new Properties());
        } else if (Map.class.isAssignableFrom(requiredType)) {
            return Optional.of((T) Collections.emptyMap());
        }
        return Optional.empty();
    }

    /**
     * Returns a combined Map of all properties in the catalog.
     *
     * @return Map of all properties
     */
    public Map<String, Object> getAllProperties() {
        Map<String, Object> map = new HashMap<>();

        Arrays
            .stream(catalog)
            .filter(Objects::nonNull)
            .map(Map::entrySet)
            .flatMap(Collection::stream)
            .forEach((Map.Entry<String, Object> entry) -> {
                String k = entry.getKey();
                Object value = resolvePlaceHoldersIfNecessary(entry.getValue());
                Map finalMap = map;
                int index = k.indexOf('.');
                if (index != -1) {
                    String[] keys = k.split("\\.");
                    for (int i = 0; i < keys.length - 1; i++) {
                        if (!finalMap.containsKey(keys[i])) {
                            finalMap.put(keys[i], new HashMap<>());
                        }
                        Object next = finalMap.get(keys[i]);
                        if (next instanceof Map) {
                            finalMap = ((Map) next);
                        }
                    }
                    finalMap.put(keys[keys.length - 1], value);
                } else {
                    finalMap.put(k, value);
                }
            });

        return map;
    }

    /**
     * @param name              The property name
     * @param entries           The entries
     * @param conversionContext The conversion context
     * @return The subproperties
     */
    protected Properties resolveSubProperties(String name, Map<String, Object> entries, ArgumentConversionContext<?> conversionContext) {
        // special handling for maps for resolving sub keys
        Properties properties = new Properties();
        AnnotationMetadata annotationMetadata = conversionContext.getAnnotationMetadata();
        StringConvention keyConvention = annotationMetadata.getValue(MapFormat.class, "keyFormat", StringConvention.class)
                                                           .orElse(StringConvention.RAW);
        String prefix = name + '.';
        entries.entrySet().stream()
            .filter(map -> map.getKey().startsWith(prefix))
            .forEach(entry -> {
                Object value = entry.getValue();
                if (value != null) {
                    String key = entry.getKey().substring(prefix.length());
                    key = keyConvention.format(key);
                    properties.put(key, resolvePlaceHoldersIfNecessary(value.toString()));
                }
            });

        return properties;
    }

    /**
     * @param name              The property name
     * @param entries           The entries
     * @param conversionContext The conversion context
     * @return The submap
     */
    protected Map<String, Object> resolveSubMap(String name, Map<String, Object> entries, ArgumentConversionContext<?> conversionContext) {
        // special handling for maps for resolving sub keys
        Map<String, Object> subMap = new LinkedHashMap<>();
        AnnotationMetadata annotationMetadata = conversionContext.getAnnotationMetadata();
        StringConvention keyConvention = annotationMetadata.getValue(MapFormat.class, "keyFormat", StringConvention.class).orElse(StringConvention.RAW);
        String prefix = name + '.';
        for (Map.Entry<String, Object> map : entries.entrySet()) {
            if (map.getKey().startsWith(prefix)) {
                String subMapKey = map.getKey().substring(prefix.length());
                Object value = resolvePlaceHoldersIfNecessary(map.getValue());
                MapFormat.MapTransformation transformation = annotationMetadata.getValue(
                        MapFormat.class,
                        "transformation",
                        MapFormat.MapTransformation.class)
                        .orElse(MapFormat.MapTransformation.NESTED);

                if (transformation == MapFormat.MapTransformation.FLAT) {
                    subMapKey = keyConvention.format(subMapKey);
                    subMap.put(subMapKey, value);
                } else {
                    int index = subMapKey.indexOf('.');
                    if (index == -1) {
                        subMapKey = keyConvention.format(subMapKey);
                        subMap.put(subMapKey, value);
                    } else {

                        String mapKey = subMapKey.substring(0, index);
                        mapKey = keyConvention.format(mapKey);
                        if (!subMap.containsKey(mapKey)) {
                            subMap.put(mapKey, new LinkedHashMap<>());
                        }
                        Map<String, Object> nestedMap = (Map<String, Object>) subMap.get(mapKey);
                        String nestedKey = subMapKey.substring(index + 1);
                        keyConvention.format(nestedKey);
                        nestedMap.put(nestedKey, value);
                    }
                }
            }
        }
        return subMap;
    }

    /**
     * @param properties The property source
     * @param convention The property convention
     */
    @SuppressWarnings("MagicNumber")
    protected void processPropertySource(PropertySource properties, PropertySource.PropertyConvention convention) {
        this.propertySources.put(properties.getName(), properties);
        synchronized (catalog) {
            for (String property : properties) {

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing property key {}", property);
                }

                Object value = properties.get(property);

                if (value instanceof String) {
                    String str = (String) value;
                    if (convention != PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE && str.contains(propertyPlaceholderResolver.getPrefix())) {
                        StringBuffer newValue = new StringBuffer();
                        Matcher matcher = RANDOM_PATTERN.matcher(str);
                        boolean hasRandoms = false;
                        while (matcher.find()) {
                            hasRandoms = true;
                            String type = matcher.group(1).trim().toLowerCase();
                            String randomValue;
                            switch (type) {
                                case "port":
                                    randomValue = String.valueOf(SocketUtils.findAvailableTcpPort());
                                    break;
                                case "int":
                                case "integer":
                                    randomValue = String.valueOf(random.nextInt());
                                    break;
                                case "long":
                                    randomValue = String.valueOf(random.nextLong());
                                    break;
                                case "float":
                                    randomValue = String.valueOf(random.nextFloat());
                                    break;
                                case "shortuuid":
                                    randomValue = UUID.randomUUID().toString().substring(25, 35);
                                    break;
                                case "uuid":
                                    randomValue = UUID.randomUUID().toString();
                                    break;
                                case "uuid2":
                                    randomValue = UUID.randomUUID().toString().replaceAll("-", "");
                                    break;
                                default:
                                    throw new ConfigurationException("Invalid random expression " + matcher.group(0) + " for property: " + property);
                            }
                            matcher.appendReplacement(newValue, randomValue);
                        }

                        if (hasRandoms) {
                            matcher.appendTail(newValue);
                            value = newValue.toString();
                        }

                    }


                }

                List<String> resolvedProperties = resolvePropertiesForConvention(property, convention);
                for (String resolvedProperty : resolvedProperties) {
                    int i = resolvedProperty.indexOf('[');
                    if (i > -1 && resolvedProperty.endsWith("]")) {
                        String index = resolvedProperty.substring(i + 1, resolvedProperty.length() - 1);
                        if (StringUtils.isNotEmpty(index)) {
                            resolvedProperty = resolvedProperty.substring(0, i);
                            Map entries = resolveEntriesForKey(resolvedProperty, true);
                            Object v = entries.get(resolvedProperty);
                            if (StringUtils.isDigits(index)) {
                                Integer number = Integer.valueOf(index);
                                List list;
                                if (v instanceof List) {
                                    list = (List) v;
                                } else {
                                    list = new ArrayList(number);
                                    entries.put(resolvedProperty, list);
                                }
                                list.add(number, value);
                            } else {
                                Map map;
                                if (v instanceof Map) {
                                    map = (Map) v;
                                } else {
                                    map = new LinkedHashMap(10);
                                    entries.put(resolvedProperty, map);
                                }
                                map.put(index, value);
                            }
                        }
                    } else {

                        Map entries = resolveEntriesForKey(resolvedProperty, true);
                        if (entries != null) {
                            entries.put(resolvedProperty, value);
                        }
                    }
                }
            }
        }
    }

    /**
     * @param name        The name
     * @param allowCreate Whether allows creation
     * @return The map with the resolved entries for the name
     */
    @SuppressWarnings("MagicNumber")
    protected Map<String, Object> resolveEntriesForKey(String name, boolean allowCreate) {
        Map<String, Object> entries = null;
        if (name.length() == 0) {
            return null;
        }
        char firstChar = name.charAt(0);
        if (Character.isLetter(firstChar)) {
            int index = ((int) firstChar) - 65;
            if (index < catalog.length && index > 0) {
                entries = catalog[index];
                if (allowCreate && entries == null) {
                    entries = new LinkedHashMap<>(5);
                    catalog[index] = entries;
                }
            }
        }
        return entries;
    }

    /**
     * Subclasses can override to reset caches.
     */
    protected void resetCaches() {
        containsCache.clear();
        resolvedValueCache.clear();
    }

    private String normalizeName(String name) {
        return name.replace('-', '.');
    }

    private Object resolvePlaceHoldersIfNecessary(Object value) {
        if (value instanceof CharSequence) {
            return propertyPlaceholderResolver.resolveRequiredPlaceholders(value.toString());
        }
        return value;
    }

    private List<String> resolvePropertiesForConvention(String property, PropertySource.PropertyConvention convention) {
        switch (convention) {
            case ENVIRONMENT_VARIABLE:
                // environment variables are converted to lower case and dot separated
                return Collections.singletonList(property.toLowerCase(Locale.ENGLISH)
                        .replace('_', '.'));
            default:
                return Collections.singletonList(
                        NameUtils.hyphenate(property, true)
                );
        }
    }

    private String trimIndex(String name) {
        int i = name.indexOf('[');
        if (i > -1 && name.endsWith("]")) {
            name = name.substring(0, i);
        }
        return name;
    }
}
