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

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link OptionalValues}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MapOptionalValues<T> implements OptionalValues<T> {
    private final Class<?> type;
    private final Map<CharSequence, ?> values;
    private final ValueResolver resolver;

    public MapOptionalValues(Class<?> type, Map<CharSequence, ?> values) {
        this.type = type;
        this.values = values;
        this.resolver = ValueResolver.of(values);
    }

    @Override
    public Optional<T> get(CharSequence name) {
        return resolver.get(name, (Class)type);
    }

    @Override
    public Iterator<CharSequence> iterator() {
        return values.keySet().iterator();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MapOptionalValues that = (MapOptionalValues) o;

        return values.equals(that.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }
}
