/*
 * Copyright 2017 original authors
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
package org.particleframework.core.value;

import org.particleframework.core.convert.ConversionService;

import java.util.Map;
import java.util.Optional;

/**
 * A simple map based implementation of the {@link ValueResolver} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MapValueResolver implements ValueResolver {
    final Map<CharSequence, ?> map;

    MapValueResolver(Map<CharSequence, ?> map) {
        this.map = map;
    }

    @Override
    public <T> Optional<T> get(CharSequence name, Class<T> requiredType) {
        Object v = map.get(name);
        if(v == null) return Optional.empty();

        if(requiredType.isInstance(v)) {
            return Optional.of((T) v);
        }
        return ConversionService.SHARED.convert(v, requiredType);
    }
}
