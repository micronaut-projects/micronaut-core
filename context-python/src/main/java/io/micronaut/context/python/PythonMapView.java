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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * The Java view of a Python {@code dict} attribute: reads convert the Python keys and values to the
 * declared types, writes convert the Java values for the Python context. Serialization writes a plain
 * {@link LinkedHashMap} copy of the entries.
 *
 * @param <K> The key type
 * @param <V> The value type
 * @since 5.2.0
 */
@Internal
final class PythonMapView<K, V> extends AbstractMap<K, V> implements PythonCollectionView, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Value dict;
    private final transient Class<K> keyType;
    private final transient Class<V> valueType;

    PythonMapView(Value dict, Class<K> keyType, Class<V> valueType) {
        this.dict = dict;
        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Override
    public Value pythonValue() {
        return dict;
    }

    @Override
    public int size() {
        return (int) dict.getHashSize();
    }

    @Override
    public boolean containsKey(Object key) {
        return dict.hasHashEntry(pythonKey(key));
    }

    @Override
    public @Nullable V get(Object key) {
        Object pythonKey = pythonKey(key);
        if (!dict.hasHashEntry(pythonKey)) {
            return null;
        }
        return PythonCoercion.viewElement(dict.getHashValue(pythonKey), valueType);
    }

    @Override
    public @Nullable V put(K key, @Nullable V value) {
        V previous = get(key);
        dict.putHashEntry(pythonKey(key), PythonCoercion.viewValue(value, dict.getContext()));
        return previous;
    }

    @Override
    public @Nullable V remove(Object key) {
        Object pythonKey = pythonKey(key);
        if (!dict.hasHashEntry(pythonKey)) {
            return null;
        }
        V previous = PythonCoercion.viewElement(dict.getHashValue(pythonKey), valueType);
        dict.removeHashEntry(pythonKey);
        return previous;
    }

    @Override
    public void clear() {
        dict.invokeMember("clear");
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return new EntrySet();
    }

    private Object pythonKey(@Nullable Object key) {
        Object pythonKey = PythonCoercion.viewValue(key, dict.getContext());
        return pythonKey == null ? dict.getContext().asValue(null) : pythonKey;
    }

    @Serial
    private Object writeReplace() {
        return new LinkedHashMap<>(this);
    }

    /**
     * The entries in Python iteration order; an entry writes its value through to the dict.
     */
    private final class EntrySet extends AbstractSet<Entry<K, V>> {

        @Override
        public Iterator<Entry<K, V>> iterator() {
            Value entries = dict.getHashEntriesIterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return entries.hasIteratorNextElement();
                }

                @Override
                public Entry<K, V> next() {
                    if (!entries.hasIteratorNextElement()) {
                        throw new NoSuchElementException();
                    }
                    Value entry = entries.getIteratorNextElement();
                    Value pythonKey = entry.getArrayElement(0);
                    K key = PythonCoercion.viewElement(pythonKey, keyType);
                    V value = PythonCoercion.viewElement(entry.getArrayElement(1), valueType);
                    return new SimpleEntry<>(key, value) {
                        @Serial
                        private static final long serialVersionUID = 1L;

                        @Override
                        public V setValue(V newValue) {
                            dict.putHashEntry(pythonKey, PythonCoercion.viewValue(newValue, dict.getContext()));
                            return super.setValue(newValue);
                        }
                    };
                }
            };
        }

        @Override
        public int size() {
            return PythonMapView.this.size();
        }
    }
}
