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
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.RandomAccess;

/**
 * The Java view of a Python {@code list} attribute: element reads convert the Python element to the
 * declared element type, element writes convert the Java value for the Python context. Serialization
 * writes a plain {@link ArrayList} copy of the elements.
 *
 * @param <E> The element type
 * @since 5.2.0
 */
@Internal
@SuppressWarnings("java:S2160") // AbstractList provides value-based equality over this view's elements.
final class PythonListView<E> extends AbstractList<E> implements PythonCollectionView, RandomAccess, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient Value list;
    private final transient Class<E> elementType;

    PythonListView(Value list, Class<E> elementType) {
        this.list = list;
        this.elementType = elementType;
    }

    @Override
    public Value pythonValue() {
        return list;
    }

    @Override
    public @Nullable E get(int index) {
        return PythonCoercion.viewElement(list.getArrayElement(index), elementType);
    }

    @Override
    public @Nullable E set(int index, @Nullable E element) {
        E previous = get(index);
        list.setArrayElement(index, PythonCoercion.viewValue(element, list.getContext()));
        return previous;
    }

    @Override
    public void add(int index, @Nullable E element) {
        list.invokeMember("insert", index, PythonCoercion.viewValue(element, list.getContext()));
    }

    @Override
    public @Nullable E remove(int index) {
        E previous = get(index);
        list.removeArrayElement(index);
        return previous;
    }

    @Override
    public void clear() {
        list.invokeMember("clear");
    }

    @Override
    public int size() {
        return (int) list.getArraySize();
    }

    @Serial
    private Object writeReplace() {
        return new ArrayList<>(this);
    }
}
