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
package org.particleframework.core.convert;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link MutableConvertibleMultiValues} that operates against a backing {@link java.util.LinkedHashMap}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class MutableConvertibleMultiValuesMap<V> extends ConvertibleMultiValuesMap<V> implements MutableConvertibleMultiValues<V> {
    @Override
    public MutableConvertibleMultiValues<V> add(CharSequence key, V value) {
        this.values.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(value);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<V> put(CharSequence key, V value) {
        ArrayList<V> values = new ArrayList<>();
        values.add(value);
        this.values.put(key , values);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<V> remove(CharSequence key, V value) {
        this.values.computeIfAbsent(key, k -> new ArrayList<>())
                    .remove(value);

        return this;
    }

    @Override
    public MutableConvertibleMultiValues<V> clear(CharSequence key) {
        this.values.remove(key);
        return this;
    }

    @Override
    public MutableConvertibleMultiValues<V> clear() {
        this.values.clear();
        return this;
    }
}
