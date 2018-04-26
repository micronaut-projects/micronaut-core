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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.KeyFormat;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.MapPropertyResolver;
import io.micronaut.core.value.PropertyResolver;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * <p>A {@link PropertyResolver} that resolves from one or many {@link PropertySource} instances</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertySourcePropertyResolver implements PropertyResolver {

    protected final ConversionService<?> conversionService;
    protected final PropertyPlaceholderResolver propertyPlaceholderResolver;
    protected final Map<String, PropertySource> propertySources = new ConcurrentHashMap<>(10);
    // properties are stored in an array of maps organized by character in the alphabet
    // this allows optimization of searches by prefix
    protected final Map<String, Object>[] catalog = new Map[57];

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver} for the given {@link ConversionService}
     *
     * @param conversionService The {@link ConversionService}
     */
    public PropertySourcePropertyResolver(ConversionService<?> conversionService) {
        this.conversionService = conversionService;
        this.propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(this);
    }

    /**
     * Creates a new, initially empty, {@link PropertySourcePropertyResolver}
     */
    public PropertySourcePropertyResolver() {
        this(ConversionService.SHARED);
    }

    /**
     * Creates a new {@link PropertySourcePropertyResolver} for the given {@link PropertySource} instances
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
     * Add a {@link PropertySource} to this resolver
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
     * Add a property source for the given map
     *
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

            Map<String, Object> entries = resolveEntriesForKey(name, false);
            if (entries == null) {
                return false;
            } else {
                name = trimIndex(name);
                return entries.containsKey(name) || entries.containsKey(normalizeName(name));
            }
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
                    return entries.keySet().stream().anyMatch(key -> key.startsWith(finalName));
                }
            }
        }
    }

    @Override
    public <T> Optional<T> getProperty(@Nullable String name, ArgumentConversionContext<T> conversionContext) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
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
                Class<T> requiredType = conversionContext.getArgument().getType();
                if (value != null) {
                    value = resolvePlaceHoldersIfNecessary(value);
                    return conversionService.convert(value, conversionContext);
                } else if (Properties.class.isAssignableFrom(requiredType)) {
                    Properties properties = resolveSubProperties(name, entries, conversionContext);
                    return Optional.of((T) properties);
                } else if (Map.class.isAssignableFrom(requiredType)) {
                    Map<String, Object> subMap = resolveSubMap(name, entries);
                    return conversionService.convert(subMap, requiredType, conversionContext);
                } else if (PropertyResolver.class.isAssignableFrom(requiredType)) {
                    Map<String, Object> subMap = resolveSubMap(name, entries);
                    return Optional.of((T) new MapPropertyResolver(subMap, conversionService));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns a combined Map of all properties in the catalog
     *
     * @return Map of all properties
     */
    public Map<String,Object> getAllProperties(){
        Map<String, Object> map = new HashMap<>();

        Arrays.stream(catalog)
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
                        for (int i = 0; i < keys.length -1; i++) {
                            if (!finalMap.containsKey(keys[i])) {
                                finalMap.put(keys[i], new HashMap<>());
                            }
                            Object next = finalMap.get(keys[i]);
                            if (next instanceof Map) {
                                finalMap = ((Map) next);
                            }
                        }
                        finalMap.put(keys[keys.length -1], value);
                    } else {
                        finalMap.put(k, value);
                    }
                });

        return map;
    }

    private String normalizeName(String name) {
        return name.toLowerCase(Locale.ENGLISH).replace('-', '.');
    }

    private Object resolvePlaceHoldersIfNecessary(Object value) {
        if (value instanceof CharSequence) {
            return propertyPlaceholderResolver.resolveRequiredPlaceholders(value.toString());
        }
        return value;
    }


    protected Properties resolveSubProperties(String name, Map<String, Object> entries, ArgumentConversionContext<?> conversionContext) {
        // special handling for maps for resolving sub keys
        Properties properties = new Properties();
        KeyFormat keyFormat = conversionContext.getAnnotation(KeyFormat.class);
        String prefix = name + '.';
        entries.entrySet().stream()
                .filter(map -> map.getKey().startsWith(prefix))
                .forEach(entry -> {
                    Object value = entry.getValue();
                    if (value != null) {
                        String key = entry.getKey().substring(prefix.length());
                        if(keyFormat != null) {
                            key = keyFormat.value().format(key);
                        }
                        properties.put(key, resolvePlaceHoldersIfNecessary(value.toString()));
                    }
                });

        return properties;
    }

    protected Map<String, Object> resolveSubMap(String name, Map<String, Object> entries) {
        // special handling for maps for resolving sub keys
        Map<String, Object> subMap = new LinkedHashMap<>();
        String prefix = name + '.';
        for (Map.Entry<String, Object> map : entries.entrySet()) {
            if (map.getKey().startsWith(prefix)) {
                String subMapKey = map.getKey().substring(prefix.length());
                int index = subMapKey.indexOf('.');
                Object value = resolvePlaceHoldersIfNecessary(map.getValue());
                if (index == -1) {
                    subMap.put(subMapKey, value);
                } else {
                    String mapKey = subMapKey.substring(0, index);
                    if (!subMap.containsKey(mapKey)) {
                        subMap.put(mapKey, new LinkedHashMap<>());
                    }
                    Map<String, Object> nestedMap = (Map<String, Object>) subMap.get(mapKey);
                    nestedMap.put(subMapKey.substring(index + 1), value);
                }
            }
        }
        return subMap;
    }

    protected void processPropertySource(PropertySource properties, PropertySource.PropertyConvention convention) {
        this.propertySources.put(properties.getName(), properties);
        synchronized (catalog) {
            for (String property : properties) {
                Object value = properties.get(property);

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

    private List<String> resolvePropertiesForConvention(String property, PropertySource.PropertyConvention convention) {
        switch (convention) {
            case ENVIRONMENT_VARIABLE:
                // environment variables are converted to lower case and dot separated
                return Collections.singletonList(property.toLowerCase(Locale.ENGLISH).replace('_', '.'));
            default:
                return Collections.singletonList(
                        NameUtils.hyphenate(property, true)
                );
        }
    }


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

    private String trimIndex(String name) {
        int i = name.indexOf('[');
        if (i > -1 && name.endsWith("]")) {
            name = name.substring(0, i);
        }
        return name;
    }
}
