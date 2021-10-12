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
package io.micronaut.core.value;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;

import java.util.Map;
import java.util.Optional;

/**
 * A simple map based implementation of the {@link ValueResolver} interface.
 *
 * @param <K> The key type
 * @author Graeme Rocher
 * @since 1.0
 */
class MapValueResolver<K extends CharSequence> implements ValueResolver<K> {
    private final Map<K, ?> map;

    /**
     * @param map The map
     */
    MapValueResolver(Map<K, ?> map) {
        this.map = map;
    }

    @Override
    public <T> Optional<T> get(K name, ArgumentConversionContext<T> conversionContext) {
        Object v = map.get(name);
        if (v == null) {
            return Optional.empty();
        }

        Argument<T> argument = conversionContext.getArgument();
        if (argument.getType().isInstance(v)) {
            return Optional.of((T) v);
        }
        return ConversionService.SHARED.convert(v, conversionContext);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MapValueResolver that = (MapValueResolver) o;

        return map.equals(that.map);
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }
}
