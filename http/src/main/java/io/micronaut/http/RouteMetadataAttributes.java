/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * The attribute map of a {@link RouteMetadataHolder}. The {@link HttpAttributes#ROUTE_MATCH},
 * {@link HttpAttributes#ROUTE_INFO} and {@link HttpAttributes#URI_TEMPLATE} entries are not
 * stored in this map: every read and write of those keys goes to the typed accessors of the
 * holder, which are the only store for them. So the map never holds a copy of the route metadata
 * that could be older than the holder's fields, and creating the map does not have to move the
 * metadata into it. The other attributes are kept in a plain map, which, like the attribute map
 * of any message, is not safe for concurrent modification.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@SuppressWarnings("removal")
public final class RouteMetadataAttributes implements MutableConvertibleValues<Object> {

    private static final String ROUTE_MATCH_KEY = HttpAttributes.ROUTE_MATCH.toString();
    private static final String ROUTE_INFO_KEY = HttpAttributes.ROUTE_INFO.toString();
    private static final String URI_TEMPLATE_KEY = HttpAttributes.URI_TEMPLATE.toString();

    private final RouteMetadataHolder holder;
    private final Map<String, Object> others;

    /**
     * @param holder          The holder that stores the route metadata
     * @param initialCapacity The initial capacity of the map for the other attributes
     */
    public RouteMetadataAttributes(RouteMetadataHolder holder, int initialCapacity) {
        this.holder = holder;
        this.others = new HashMap<>(initialCapacity);
    }

    /**
     * Read one of the metadata attributes from the holder.
     *
     * @param holder The holder
     * @param key    The attribute name
     * @return The value, or {@code null} if absent or if the key is not a metadata key
     */
    public static @Nullable Object getMetadata(RouteMetadataHolder holder, String key) {
        if (key.equals(ROUTE_MATCH_KEY)) {
            return holder.getRouteMatchMetadata();
        }
        if (key.equals(ROUTE_INFO_KEY)) {
            return holder.getRouteInfoMetadata();
        }
        if (key.equals(URI_TEMPLATE_KEY)) {
            return holder.getUriTemplateMetadata();
        }
        return null;
    }

    /**
     * Write one of the metadata attributes to the holder.
     *
     * @param holder The holder
     * @param key    The attribute name
     * @param value  The value, or {@code null} to remove it
     * @return Whether the key is a metadata key, if not nothing was written
     */
    public static boolean setMetadata(RouteMetadataHolder holder, String key, @Nullable Object value) {
        if (key.equals(ROUTE_MATCH_KEY)) {
            holder.setRouteMatchMetadata(value);
            return true;
        }
        if (key.equals(ROUTE_INFO_KEY)) {
            holder.setRouteInfoMetadata(value);
            return true;
        }
        if (key.equals(URI_TEMPLATE_KEY)) {
            holder.setUriTemplateMetadata(value == null ? null : value.toString());
            return true;
        }
        return false;
    }

    /**
     * @param key The attribute name
     * @return Whether the key is one of the metadata keys stored by the holder
     */
    public static boolean isMetadataKey(String key) {
        return key.equals(ROUTE_MATCH_KEY) || key.equals(ROUTE_INFO_KEY) || key.equals(URI_TEMPLATE_KEY);
    }

    @Override
    public @Nullable Object getValue(CharSequence name) {
        if (name == null) {
            return null;
        }
        String key = name.toString();
        if (isMetadataKey(key)) {
            return getMetadata(holder, key);
        }
        return others.get(key);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        Object value = getValue(name);
        if (value == null) {
            return Optional.empty();
        }
        return getConversionService().convert(value, conversionContext);
    }

    @Override
    public boolean contains(String name) {
        return getValue(name) != null;
    }

    @Override
    public Set<String> names() {
        Set<String> names = new HashSet<>(others.keySet());
        forEachMetadata((k, v) -> names.add(k));
        return names;
    }

    @Override
    public Collection<Object> values() {
        Collection<Object> values = new ArrayList<>(others.values());
        forEachMetadata((k, v) -> values.add(v));
        return values;
    }

    @Override
    public boolean isEmpty() {
        return others.isEmpty()
            && holder.getRouteMatchMetadata() == null
            && holder.getRouteInfoMetadata() == null
            && holder.getUriTemplateMetadata() == null;
    }

    @Override
    public void forEach(BiConsumer<String, Object> action) {
        forEachMetadata(action);
        others.forEach(action);
    }

    private void forEachMetadata(BiConsumer<String, Object> action) {
        Object routeMatch = holder.getRouteMatchMetadata();
        if (routeMatch != null) {
            action.accept(ROUTE_MATCH_KEY, routeMatch);
        }
        Object routeInfo = holder.getRouteInfoMetadata();
        if (routeInfo != null) {
            action.accept(ROUTE_INFO_KEY, routeInfo);
        }
        String uriTemplate = holder.getUriTemplateMetadata();
        if (uriTemplate != null) {
            action.accept(URI_TEMPLATE_KEY, uriTemplate);
        }
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence name, @Nullable Object value) {
        String key = name.toString();
        if (!setMetadata(holder, key, value)) {
            if (value == null) {
                others.remove(key);
            } else {
                others.put(key, value);
            }
        }
        return this;
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence name) {
        String key = name.toString();
        if (!setMetadata(holder, key, null)) {
            others.remove(key);
        }
        return this;
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        holder.setRouteMatchMetadata(null);
        holder.setRouteInfoMetadata(null);
        holder.setUriTemplateMetadata(null);
        others.clear();
        return this;
    }

    @Override
    public String toString() {
        return asMap().toString();
    }
}
