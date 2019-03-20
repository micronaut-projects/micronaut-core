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

import io.micronaut.context.annotation.Property;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.MapPropertyResolver;
import io.micronaut.core.value.PropertyResolver;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>A {@link PropertyResolver} that resolves from one or many {@link PropertySource} instances.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertySourcePropertyResolver implements PropertyResolver {

    private static final Logger LOG = ClassUtils.getLogger(PropertySourcePropertyResolver.class);
    private static final Pattern RANDOM_PATTERN = Pattern.compile("\\$\\{\\s?random\\.(\\S+?)\\}");

    protected final ConversionService<?> conversionService;
    protected final PropertyPlaceholderResolver propertyPlaceholderResolver;
    protected final Map<String, PropertySource> propertySources = new ConcurrentHashMap<>(10);
    // properties are stored in an array of maps organized by character in the alphabet
    // this allows optimization of searches by prefix
    @SuppressWarnings("MagicNumber")
    protected final Map<String, Object>[] catalog = new Map[58];
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
        this.propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(this, conversionService);
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
    public @Nonnull Map<String, Object> getProperties(String name, StringConvention keyFormat) {
        if (keyFormat == null) {
            keyFormat = StringConvention.RAW;
        }
        if (!StringUtils.isEmpty(name)) {
            Map<String, Object> entries = resolveEntriesForKey(name, false);
            if (entries != null) {
                return resolveSubMap(
                        name,
                        entries,
                        ConversionContext.of(Map.class),
                        keyFormat,
                        MapFormat.MapTransformation.FLAT
                );
            }
        }
        return Collections.emptyMap();
    }

    @Override
    public <T> Optional<T> getProperty(@Nonnull String name, @Nonnull ArgumentConversionContext<T> conversionContext) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        } else {
            ArgumentUtils.requireNonNull("conversionContext", conversionContext);
            Class<T> requiredType = conversionContext.getArgument().getType();
            boolean cacheableType = requiredType == Boolean.class || requiredType == String.class;
            String cacheName = name + '|' + requiredType.getSimpleName();
            if (cacheableType && resolvedValueCache.containsKey(cacheName)) {
                return (Optional<T>) resolvedValueCache.get(cacheName);
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
                            resolvedValueCache.put(cacheName, converted);
                        }
                        return converted;
                    } else if (cacheableType) {
                        Optional<?> e = Optional.empty();
                        resolvedValueCache.put(cacheName, e);
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
        AnnotationMetadata annotationMetadata = conversionContext.getAnnotationMetadata();
        StringConvention keyConvention = annotationMetadata.getValue(MapFormat.class, "keyFormat", StringConvention.class).orElse(StringConvention.RAW);
        MapFormat.MapTransformation transformation = annotationMetadata.getValue(
                MapFormat.class,
                "transformation",
                MapFormat.MapTransformation.class)
                .orElse(conversionContext.isAnnotationPresent(Property.class) ? MapFormat.MapTransformation.FLAT : MapFormat.MapTransformation.NESTED);
        return resolveSubMap(name, entries, conversionContext, keyConvention, transformation);
    }

    /**
     * Resolves a submap for the given name and parameters.
     *
     * @param name The name
     * @param entries The entries
     * @param conversionContext The conversion context
     * @param keyConvention The key convention to use
     * @param transformation The map transformation to apply
     * @return The resulting map
     */
    @Nonnull
    protected Map<String, Object> resolveSubMap(
            String name,
            Map<String, Object> entries,
            ArgumentConversionContext<?> conversionContext,
            StringConvention keyConvention,
            MapFormat.MapTransformation transformation) {
        final Argument<?> valueType = conversionContext.getTypeVariable("V").orElse(Argument.OBJECT_ARGUMENT);
        Map<String, Object> subMap = new LinkedHashMap<>(entries.size());

        String prefix = name + '.';
        for (Map.Entry<String, Object> entry : entries.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith(prefix)) {
                String subMapKey = key.substring(prefix.length());
                Object value = resolvePlaceHoldersIfNecessary(entry.getValue());

                if (transformation == MapFormat.MapTransformation.FLAT) {
                    subMapKey = keyConvention.format(subMapKey);
                    value = conversionService.convert(value, valueType).orElse(null);
                    subMap.put(subMapKey, value);
                } else {
                    processSubmapKey(
                            subMap,
                            subMapKey,
                            value,
                            keyConvention
                    );
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

                if (value instanceof CharSequence) {
                    value = processRandomExpressions(convention, property, (CharSequence) value);
                } else if (value instanceof List) {
                    final ListIterator i = ((List) value).listIterator();
                    while (i.hasNext()) {
                        final Object o = i.next();
                        if (o instanceof CharSequence) {
                            final CharSequence newValue = processRandomExpressions(convention, property, (CharSequence) o);
                            if (newValue != o) {
                                i.set(newValue);
                            }
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
                                    list = new ArrayList(10);
                                    entries.put(resolvedProperty, list);
                                }
                                fill(list, number, null);
                                list.set(number, value);
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

    private CharSequence processRandomExpressions(PropertySource.PropertyConvention convention, String property, CharSequence str) {
        if (convention != PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE && str.toString().contains(propertyPlaceholderResolver.getPrefix())) {
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
                return newValue.toString();
            }

        }
        return str;
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

    private void processSubmapKey(Map<String, Object> map, String key, Object value, StringConvention keyConvention) {
        int index = key.indexOf('.');
        if (index == -1) {
            key = keyConvention.format(key);
            map.put(key, value);
        } else {

            String mapKey = key.substring(0, index);
            mapKey = keyConvention.format(mapKey);
            if (!map.containsKey(mapKey)) {
                map.put(mapKey, new LinkedHashMap<>());
            }
            final Object v = map.get(mapKey);
            if (v instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) v;
                String nestedKey = key.substring(index + 1);
                processSubmapKey(nestedMap, nestedKey, value, keyConvention);
            } else {
                map.put(mapKey, v);
            }
        }
    }

    private String normalizeName(String name) {
        return name.replace('-', '.');
    }

    private Object resolvePlaceHoldersIfNecessary(Object value) {
        if (value instanceof CharSequence) {
            return propertyPlaceholderResolver.resolveRequiredPlaceholders(value.toString());
        } else if (value instanceof List) {
            List<?> list = (List) value;
            List<?> newList = new ArrayList<>(list);
            final ListIterator i = newList.listIterator();
            while (i.hasNext()) {
                final Object o = i.next();
                if (o instanceof CharSequence) {
                    i.set(resolvePlaceHoldersIfNecessary(o));
                } else if (o instanceof Map) {
                    Map<?, ?> submap = (Map) o;
                    Map<Object, Object> newMap = new LinkedHashMap<>(submap.size());
                    for (Map.Entry<?, ?> entry : submap.entrySet()) {
                        final Object k = entry.getKey();
                        final Object v = entry.getValue();
                        newMap.put(k, resolvePlaceHoldersIfNecessary(v));
                    }
                    i.set(newMap);
                }
            }
            value = newList;
        }
        return value;
    }

    private List<String> resolvePropertiesForConvention(String property, PropertySource.PropertyConvention convention) {
        switch (convention) {
            case ENVIRONMENT_VARIABLE:
                String[] tokens = property.split("_");
                Set<String> properties = new HashSet<>(tokens.length);

                if (tokens.length > 1) {
                    properties.addAll(generatePropertiesCombinations(tokens, new StringBuilder(), '.'));
                    properties.addAll(generatePropertiesCombinations(tokens, new StringBuilder(), '-'));
                    return new ArrayList<>(properties);
                } else {
                    return Collections.singletonList(property.toLowerCase(Locale.ENGLISH));
                }
            default:
                return Collections.singletonList(
                        NameUtils.hyphenate(property, true)
                );
        }
    }

    private List<String> generatePropertiesCombinations(String[] tokens, StringBuilder path, Character separator) {
        Set<String> properties = new HashSet<>(tokens.length);
        int len = tokens.length;
        StringBuilder tmpPath = new StringBuilder(path.toString());
        for (int i = 0; i < len; i++) {
            String token = tokens[i];
            if (i < (len - 1)) {
                tmpPath.append(token.toLowerCase(Locale.ENGLISH)).append(separator);
                String[] subTokens = Arrays.copyOfRange(tokens, i + 1, len);
                properties.add(tmpPath + Arrays.stream(subTokens).map(s -> s.toLowerCase(Locale.ENGLISH)).collect(Collectors.joining(".")));
                properties.add(tmpPath + Arrays.stream(subTokens).map(s -> s.toLowerCase(Locale.ENGLISH)).collect(Collectors.joining("-")));

                properties.addAll(generatePropertiesCombinations(subTokens, tmpPath, '.'));
                properties.addAll(generatePropertiesCombinations(subTokens, tmpPath, '-'));
            }
        }

        return new ArrayList<>(properties);
    }

    private String trimIndex(String name) {
        int i = name.indexOf('[');
        if (i > -1 && name.endsWith("]")) {
            name = name.substring(0, i);
        }
        return name;
    }

    private void fill(List list, Integer toIndex, Object value) {
        if (toIndex >= list.size()) {
            for (int i = list.size(); i <= toIndex; i++) {
                list.add(i, value);
            }
        }
    }
}
