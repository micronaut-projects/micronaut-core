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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;

/**
 * Runtime utility class for converting GraalPy Values to Java collections.
 * Provides type-safe conversion methods for List, Map, and other collection types.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Internal
public final class GraalPyRuntimeUtil {

    public static final String PYTHON = "python";

    /**
     * Returns whether the value represents Java null or Python None.
     *
     * @param value The polyglot value
     * @return Whether the value represents Java null or Python None
     */
    public static boolean isNone(@Nullable Value value) {
        if (value == null || value.isNull()) {
            return true;
        }
        try {
            Value metaObject = value.getMetaObject();
            if (metaObject != null && metaObject.isMetaObject() && "NoneType".equals(metaObject.getMetaSimpleName())) {
                return true;
            }
        } catch (Exception e) {
            // Ignore and fall back to the conservative textual check below.
        }
        try {
            return !value.isBoolean()
                && !value.isNumber()
                && !value.isString()
                && !value.hasArrayElements()
                && "None".equals(value.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Coerce a map of types that may extend from {@link ValueCoercible} back to a native value map.
     * @param map The map
     * @param <V> The value type of the map
     * @return The resulting map
     */
    public static <V> @Nullable Map<String, Object> coerceMap(@Nullable Map<String, V> map) {
        if (map == null) {
            return null;
        }
        return
            map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry) -> {
                Object v = entry.getValue();
                if (v instanceof ValueCoercible valueCoercible && !(v instanceof PooledValueCoercible)) {
                    return valueCoercible.asPolyglotValue();
                }
                return v;
            }));
    }

    /**
     * Coerce a list of types that may extend from {@link ValueCoercible} back to a native value list.
     * @param list The list
     * @param <E> The element type of the list
     * @return The resulting list
     *
     */
    public static <E> @Nullable List<Object> coerceList(@Nullable List<E> list) {
        if (list == null) {
            return null;
        }
        return
            list.stream().map(v -> {
                if (v instanceof ValueCoercible valueCoercible && !(v instanceof PooledValueCoercible)) {
                    return valueCoercible.asPolyglotValue();
                }
                return v;
            }).toList();
    }

    /**
     * Coerce values passed into a target Python context.
     *
     * @param value The value to coerce
     * @param context The target context
     * @return The coerced value
     */
    public static @Nullable Object coerceToContext(@Nullable Object value, Context context) {
        if (value == null) {
            return null;
        }
        if (value instanceof PooledValueCoercible pooledValueCoercible) {
            return pooledValueCoercible.asPolyglotValue(context);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object element : list) {
                result.add(coerceToContext(element, context));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(coerceToContext(entry.getKey(), context), coerceToContext(entry.getValue(), context));
            }
            return result;
        }
        if (value instanceof Set<?> set) {
            java.util.Set<Object> result = new java.util.HashSet<>();
            for (Object element : set) {
                result.add(coerceToContext(element, context));
            }
            return result;
        }
        if (value instanceof Object[] array) {
            Object[] result = new Object[array.length];
            for (int i = 0; i < array.length; i++) {
                result[i] = coerceToContext(array[i], context);
            }
            return result;
        }
        return value;
    }

    /**
     * Coerce arguments passed into a target Python context.
     *
     * @param context The target context
     * @param args The arguments
     * @return The coerced arguments
     */
    public static Object[] coerceArgumentsToContext(Context context, Object[] args) {
        Object[] result = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = coerceToContext(args[i], context);
        }
        return result;
    }

    /**
     * Invoke a generated bridge method on a Python receiver.
     *
     * @param receiver The Python receiver
     * @param name The method name
     * @param arguments The method arguments
     * @return The invocation result
     */
    public static Value invokePythonMethod(Value receiver, String name, Object[] arguments) {
        Value member = receiver.getMember(name);
        if (member != null && member.canExecute()) {
            return member.execute(arguments);
        }
        Value pythonClass = receiver.getMember("__class__");
        Value rawMember = pythonClass == null ? null : getRawClassMember(pythonClass, name);
        if (rawMember != null) {
            Value boundMember = bindPythonDescriptor(rawMember, receiver, pythonClass);
            if (boundMember.canExecute()) {
                return boundMember.execute(arguments);
            }
        }
        if (member == null) {
            throw new IllegalArgumentException("No Python member [" + name + "] found");
        }
        return member.execute(arguments);
    }

    /**
     * Read a Python class member directly from the MRO dictionaries, bypassing descriptor binding.
     *
     * @param pythonClass The Python class
     * @param name The member name
     * @return The raw member, or null if none exists
     */
    public static @Nullable Value getRawClassMember(Value pythonClass, String name) {
        Value member = getRawClassMemberFunction(pythonClass.getContext()).execute(pythonClass, name);
        if (isNone(member)) {
            return null;
        }
        return member;
    }

    /**
     * Bind a raw Python descriptor to a receiver when the descriptor protocol is available.
     *
     * @param descriptor The raw descriptor
     * @param receiver The receiver object
     * @param owner The owner class
     * @return The bound descriptor, or the original descriptor if it cannot be bound
     */
    public static Value bindPythonDescriptor(Value descriptor, Object receiver, Value owner) {
        Value getter = descriptor.getMember("__get__");
        if (getter != null && getter.canExecute()) {
            Value receiverValue = receiver instanceof Value value
                ? value
                : receiver instanceof ValueCoercible valueCoercible ? valueCoercible.asPolyglotValue() : null;
            if (receiverValue != null) {
                return getter.execute(receiverValue, owner);
            }
        }
        return descriptor;
    }

    private static Value getRawClassMemberFunction(Context context) {
        String functionName = "__micronaut_get_raw_class_member";
        Value bindings = context.getBindings(PYTHON);
        Value function = bindings.getMember(functionName);
        if (isNone(function)) {
            context.eval(
                PYTHON,
                """
                def __micronaut_get_raw_class_member(cls, name):
                    for base in getattr(cls, "__mro__", (cls,)):
                        namespace = getattr(base, "__dict__", {})
                        if name in namespace:
                            return namespace[name]
                    return None
                """
            );
            function = bindings.getMember(functionName);
        }
        return function;
    }

    /**
     * Convert a GraalPy Value representing a list to a Java List.
     * Recursively converts nested collections.
     *
     * @param graalValue the GraalPy Value (should be a list-like object)
     * @param elementType the expected element type for conversion
     * @param <T> the expected list element type
     * @return a Java List with converted elements
     */
    public static <T> @Nullable List<T> convertList(Value graalValue, Class<T> elementType) {
        if (isNone(graalValue)) {
            return null;
        }
        try {
            if (graalValue.isHostObject()) {
                Object host = graalValue.as(Object.class);
                if (host instanceof List<?> list) {
                    List<T> out = new ArrayList<>(list.size());
                    for (Object o : list) {
                        @SuppressWarnings("unchecked") T cast = (T) o;
                        out.add(cast);
                    }
                    return out;
                }
            }
            long size = getSize(graalValue);
            if (size == 0) {
                return List.of();
            }
            List<T> result = new ArrayList<>(Long.valueOf(size).intValue());
            for (long i = 0; i < size; i++) {
                Value elementValue = getElementAt(graalValue, i);
                if (elementValue != null) {
                    T convertedElement = convertValue(elementValue, elementType);
                    result.add(convertedElement);
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Convert a GraalPy Value representing a dict to a Java Map.
     * Recursively converts nested collections.
     *
     * @param graalValue the GraalPy Value (should be a dict-like object)
     * @param keyType the expected key type for conversion
     * @param valueType the expected value type for conversion
     * @param <K> the expected key type
     * @param <V> the expected value type
     * @return a Java Map with converted keys and values
     */
    public static <K, V> @Nullable Map<K, V> convertMap(Value graalValue, Class<K> keyType, Class<V> valueType) {
        if (isNone(graalValue)) {
            return null;
        }
        try {
            if (graalValue.isHostObject()) {
                Object host = graalValue.as(Object.class);
                if (host instanceof Map<?, ?> map) {
                    Map<K, V> out = new HashMap<>();
                    for (Map.Entry<?, ?> e : map.entrySet()) {
                        @SuppressWarnings("unchecked") K k = (K) e.getKey();
                        @SuppressWarnings("unchecked") V v = (V) e.getValue();
                        out.put(k, v);
                    }
                    return out;
                }
            }
            Map<K, V> result = new HashMap<>();
            Value keysValue = graalValue.invokeMember("keys");
            if (keysValue != null && keysValue.hasIterator()) {
                Value iterator = keysValue.invokeMember("__iter__");
                while (true) {
                    try {
                        Value nextValue = iterator.invokeMember("__next__");
                        if (nextValue == null || nextValue.isNull()) {
                            break;
                        }
                        try {
                            K convertedKey = convertValue(nextValue, keyType);
                            Value mapValue = graalValue.invokeMember("__getitem__", nextValue);
                            V convertedValue = convertValue(mapValue, valueType);
                            result.put(convertedKey, convertedValue);
                        } catch (Exception e) {
                            // Skip problematic entries
                        }
                    } catch (Exception e) {
                        break;
                    }
                }
            }
            return result;
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * Convert a GraalPy Value representing an Optional to a Java Optional.
     * Handles None -> Optional.empty() and value -> Optional.of(value)
     *
     * @param graalValue the GraalPy Value to convert
     * @param elementType the expected element type for conversion
     * @param <T> the expected optional element type
     * @return a Java Optional with the converted value or empty
     */
    @SuppressWarnings("unchecked")
    public static <T> java.util.Optional<T> convertOptional(Value graalValue, Class<T> elementType) {
        if (isNone(graalValue)) {
            return java.util.Optional.empty();
        }
        if (graalValue.isHostObject()) {
            Object hostObject = graalValue.asHostObject();
            if (hostObject instanceof Optional<?> optional) {
                if (optional.isEmpty()) {
                    return java.util.Optional.empty();
                }
                Object optionalValue = optional.get();
                if (optionalValue == null) {
                    return java.util.Optional.empty();
                }
                if (elementType.isInstance(optionalValue)) {
                    return java.util.Optional.of(elementType.cast(optionalValue));
                }
                if (optionalValue instanceof Value value) {
                    T convertedValue = convertValue(value, elementType);
                    return convertedValue == null ? java.util.Optional.empty() : java.util.Optional.of(convertedValue);
                }
            }
        }

        // Convert the value and wrap in Optional
        T convertedValue = convertValue(graalValue, elementType);
        if (convertedValue == null) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(convertedValue);
    }

    /**
     * Convert a GraalPy Value representing a set to a Java Set.
     * Recursively converts nested collections.
     *
     * @param graalValue the GraalPy Value (should be a set-like object)
     * @param elementType the expected element type for conversion
     * @param <T> the expected set element type
     * @return a Java Set with converted elements
     */
    public static <T> @Nullable Set<T> convertSet(Value graalValue, Class<T> elementType) {
        // TODO: Ideally a custom Set implementation that doesn't create a new map would be better here
        if (isNone(graalValue)) {
            return null;
        }

        java.util.Set<T> result = new java.util.HashSet<>();

        try {
            // Try to iterate directly over the set
            if (graalValue.hasIterator()) {
                Value iterator = graalValue.invokeMember("__iter__");
                while (true) {
                    try {
                        Value nextValue = iterator.invokeMember("__next__");
                        if (nextValue == null || nextValue.isNull()) {
                            break;
                        }

                        try {
                            T convertedElement = convertValue(nextValue, elementType);
                            result.add(convertedElement);
                        } catch (Exception e) {
                            // Skip problematic elements
                        }
                    } catch (Exception e) {
                        // Iterator exhausted (StopIteration in Python)
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // If direct iteration fails, try converting to list first
            try {
                Value listValue = graalValue.invokeMember("list");
                if (listValue != null) {
                    List<T> list = convertList(listValue, elementType);
                    if (list != null) {
                        result.addAll(list);
                    }
                }
            } catch (Exception ex) {
                // If conversion fails, return empty set
                return new java.util.HashSet<>();
            }
        }

        return result;
    }

    /**
     * Generic value conversion method that handles primitives and recursively converts collections.
     *
     * @param value The source polyglot value
     * @param targetType The target Java type
     * @param <T> The target type
     * @return The converted value or {@code null}
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T convertValue(Value value, Class<T> targetType) {
        if (isNone(value)) {
            return null;
        }

        T mappedWrapper = convertMappedWrapper(value, targetType);
        if (mappedWrapper != null) {
            return mappedWrapper;
        }
        if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            if (hostObject instanceof ProxyObject proxyObject) {
                T converted = convertValueCoercibleProxy(proxyObject, targetType);
                if (converted != null) {
                    return converted;
                }
            }
            if (targetType.isInstance(hostObject)) {
                return targetType.cast(hostObject);
            }
        }
        return value.as(targetType);
    }

    /**
     * Convert a GraalPy-created {@link HttpResponse} and its response body to the declared Java body type.
     *
     * @param value The source polyglot response
     * @param bodyType The declared response body type
     * @param <T> The response body type
     * @return The converted response
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable HttpResponse<T> convertHttpResponse(Value value, Class<T> bodyType) {
        HttpResponse<?> response = convertValue(value, HttpResponse.class);
        if (response == null) {
            return null;
        }
        return convertHttpResponse(response, bodyType);
    }

    /**
     * Convert a response body to the declared Java body type.
     *
     * @param response The source response
     * @param bodyType The declared response body type
     * @param <T> The response body type
     * @return The converted response
     */
    @SuppressWarnings("unchecked")
    public static <T> HttpResponse<T> convertHttpResponse(HttpResponse<?> response, Class<T> bodyType) {
        Optional<?> body = response.getBody();
        if (body.isEmpty()) {
            return (HttpResponse<T>) response;
        }
        Object rawBody = body.get();
        if (rawBody == null) {
            return (HttpResponse<T>) response;
        }
        if (response instanceof MutableHttpResponse<?> mutableResponse) {
            T convertedBody = convertResponseBody(rawBody, bodyType);
            if (convertedBody == null) {
                return (HttpResponse<T>) response;
            }
            return ((MutableHttpResponse<T>) mutableResponse).body(convertedBody);
        }
        return (HttpResponse<T>) response;
    }

    private static <T> @Nullable T convertResponseBody(Object rawBody, Class<T> bodyType) {
        if (bodyType.isInstance(rawBody)) {
            return bodyType.cast(rawBody);
        }
        if (rawBody instanceof ProxyObject proxyObject && proxyObject.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            T converted = convertValueCoercibleProxy(proxyObject, bodyType);
            if (converted != null) {
                return converted;
            }
        }
        if (rawBody instanceof Value bodyValue) {
            return convertValue(bodyValue, bodyType);
        }
        try {
            return convertValue(Value.asValue(rawBody), bodyType);
        } catch (ClassCastException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
    }

    private static <T> @Nullable T convertMappedWrapper(Value value, Class<T> targetType) {
        try {
            ValueCoercible host = valueCoercibleHost(value);
            if (host != null && targetType.isInstance(host)) {
                return targetType.cast(host);
            }
            if (value.isHostObject()) {
                Object hostObject = value.asHostObject();
                if (hostObject instanceof ProxyObject proxyObject) {
                    T converted = convertValueCoercibleProxy(proxyObject, targetType);
                    if (converted != null) {
                        return converted;
                    }
                }
            }
            Object mappedObject = value.as(Object.class);
            if (mappedObject instanceof ValueCoercible && targetType.isInstance(mappedObject)) {
                return targetType.cast(mappedObject);
            }
        } catch (ClassCastException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
            return null;
        }
        return null;
    }

    private static <T> @Nullable T convertValueCoercibleProxy(ProxyObject proxyObject, Class<T> targetType) {
        if (!proxyObject.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            return null;
        }
        Object hostReference = proxyObject.getMember(ValueCoercible.HOST_OBJECT_MEMBER);
        if (hostReference instanceof ValueCoercible.HostObjectReference reference) {
            ValueCoercible host = reference.value();
            if (targetType.isInstance(host)) {
                return targetType.cast(host);
            }
            return convertValue(host.asPolyglotValue(), targetType);
        }
        return null;
    }

    private static @Nullable ValueCoercible valueCoercibleHost(Value value) {
        if (value == null || value.isNull() || !value.hasMembers() || !value.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            return null;
        }
        Value hostReferenceValue = value.getMember(ValueCoercible.HOST_OBJECT_MEMBER);
        if (hostReferenceValue == null || !hostReferenceValue.isHostObject()) {
            return null;
        }
        Object hostReference = hostReferenceValue.asHostObject();
        if (hostReference instanceof ValueCoercible.HostObjectReference reference) {
            return reference.value();
        }
        return null;
    }

    /**
     * Get the size of a GraalPy collection using various methods.
     */
    private static long getSize(Value value) {
        try {
            // Try __len__ first (Python standard)
            if (value.canInvokeMember("__len__")) {
                Value length = value.invokeMember("__len__");
                return length.asLong();
            }

            // Try len() function
            if (value.canInvokeMember("__len__")) {
                Value length = value.invokeMember("__len__");
                return length.asLong();
            }

            // For arrays, try getArraySize
            try {
                return value.getArraySize();
            } catch (Exception e) {
                // Not an array
            }

            // If all else fails, try to iterate and count
            if (value.hasIterator()) {
                long count = 0;
                Value iterator = value.invokeMember("__iter__");
                while (true) {
                    try {
                        iterator.invokeMember("__next__");
                        count++;
                    } catch (Exception e) {
                        break;
                    }
                }
                return count;
            }
        } catch (Exception e) {
            // Size determination failed
        }

        return 0;
    }

    /**
     * Get element at index from a GraalPy collection.
     */
    private static @Nullable Value getElementAt(Value collection, long index) {
        try {
            // Try array access first
            try {
                return collection.getArrayElement(index);
            } catch (Exception e) {
                // Not an array, try __getitem__
            }

            // Try __getitem__ method
            if (collection.canInvokeMember("__getitem__")) {
                return collection.invokeMember("__getitem__", index);
            }

            // Try iteration to specific index
            if (collection.hasIterator()) {
                Value iterator = collection.invokeMember("__iter__");
                for (long i = 0; i <= index; i++) {
                    Value item = iterator.invokeMember("__next__");
                    if (i == index) {
                        return item;
                    }
                }
            }
        } catch (Exception e) {
            // Element access failed
        }

        return null;
    }
}
