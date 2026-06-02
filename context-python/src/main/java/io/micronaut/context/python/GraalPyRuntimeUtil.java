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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Runtime utility class for converting GraalPy Values to Java collections.
 * Provides type-safe conversion methods for List, Map, and other collection types.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Internal
@SuppressWarnings({"checkstyle:InnerTypeLast", "checkstyle:JavadocMethod", "checkstyle:TodoComment"})
public final class GraalPyRuntimeUtil {

    public static final String PYTHON = "python";
    private static final String TRANSFERABLE_MEMBER_NAMES = "__micronaut_transferable_member_names";
    private static final String PUT_MEMBER = "__micronaut_put_member";
    private static final String ASYNC_MEMBER_VALUE = "__micronaut_async_member_value";
    private static final String INVOKE_METHOD = "__micronaut_invoke_method";
    private static final String RAW_CLASS_MEMBER = "__micronaut_get_raw_class_member";
    private static final AsyncMemberAdapter ASYNC_MEMBER_ADAPTER = new AsyncMemberAdapter();
    private static final Source PUT_MEMBER_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_put_member(target, name, value):
            setattr(target, name, value)
        """, "micronaut-put-member.py").cached(true).buildLiteral();
    private static final Source ASYNC_MEMBER_VALUE_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_async_member_value(target, adapter, context):
            import asyncio
            import inspect

            def adapt(value):
                try:
                    if inspect.isawaitable(value) or asyncio.isfuture(value):
                        return value
                except Exception:
                    pass
                adapted = adapter.adaptAwaitable(context, value)
                if adapted is not None:
                    return adapted
                return value

            class _MicronautAsyncMember:
                def __init__(self, target, adapter, context):
                    self._target = target
                    self._adapter = adapter
                    self._context = context

                def __getattr__(self, name):
                    member = getattr(self._target, name)
                    if callable(member):
                        def invoke(*args, **kwargs):
                            return adapt(member(*args, **kwargs))
                        return invoke
                    return adapt(member)

            return _MicronautAsyncMember(target, adapter, context)
        """, "micronaut-async-member-value.py").cached(true).buildLiteral();
    private static final Source TRANSFERABLE_MEMBER_NAMES_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_transferable_member_names(obj):
            try:
                return list(vars(obj).keys())
            except TypeError:
                return []
        """, "micronaut-transferable-member-names.py").cached(true).buildLiteral();
    private static final Source INVOKE_METHOD_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_invoke_method(receiver, name, arguments):
            member = getattr(receiver, name, None)
            if callable(member):
                return member(*arguments)
            cls = getattr(receiver, "__class__", None)
            if cls is not None:
                for base in getattr(cls, "__mro__", (cls,)):
                    namespace = getattr(base, "__dict__", {})
                    if name in namespace:
                        raw_member = namespace[name]
                        getter = getattr(raw_member, "__get__", None)
                        if getter is not None:
                            raw_member = getter(receiver, cls)
                        if callable(raw_member):
                            return raw_member(*arguments)
                        break
            if member is None:
                raise AttributeError(name)
            return member(*arguments)
        """, "micronaut-invoke-method.py").cached(true).buildLiteral();
    private static final Source RAW_CLASS_MEMBER_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_get_raw_class_member(cls, name):
            for base in getattr(cls, "__mro__", (cls,)):
                namespace = getattr(base, "__dict__", {})
                if name in namespace:
                    return namespace[name]
            return None
        """, "micronaut-raw-class-member.py").cached(true).buildLiteral();

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
        if (value.isHostObject()) {
            return false;
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
                Object coerced = coerceValue(v);
                return coerced instanceof PooledValueCoercible ? v : coerced;
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
                Object coerced = coerceValue(v);
                return coerced instanceof PooledValueCoercible ? v : coerced;
            }).toList();
    }

    /**
     * Coerce a generated Python-backed Java wrapper back to its native Python value.
     * @param value The value
     * @return The native Python value when available
     */
    @UsedByGeneratedCode
    public static @Nullable Object coerceValue(@Nullable Object value) {
        if (value instanceof ValueCoercible valueCoercible && !(value instanceof PooledValueCoercible)) {
            return valueCoercible.asPolyglotValue();
        }
        return value;
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
     * Assign a member on a Python value after coercing the value into the same context.
     *
     * @param target The Python object to update
     * @param name The member name
     * @param value The member value
     */
    @UsedByGeneratedCode
    public static void putMember(Value target, String name, @Nullable Object value) {
        Context context = target.getContext();
        memberSetter(context).executeVoid(target, name, coerceToContext(value, context));
    }

    /**
     * Convert an injected Java member into a Python-context-local value suitable for async code.
     *
     * @param target The target Python object receiving the member.
     * @param value The Java value to expose.
     * @return A value that adapts Java async method results to Python awaitables.
     */
    public static @Nullable Object asyncMemberValue(Value target, @Nullable Object value) {
        if (isInteropPrimitive(value)) {
            return value;
        }
        Context context = target.getContext();
        if (value instanceof CompletionStage<?> completionStage) {
            return PythonAsyncioRuntime.toAwaitable(context, completionStage);
        }
        return asyncMemberFactory(context).execute(value, ASYNC_MEMBER_ADAPTER, context);
    }

    private static boolean isInteropPrimitive(@Nullable Object value) {
        return value == null
            || value instanceof Byte
            || value instanceof Short
            || value instanceof Integer
            || value instanceof Long
            || value instanceof Float
            || value instanceof Double
            || value instanceof Boolean
            || value instanceof Character
            || value instanceof String;
    }

    private static boolean isInteropPrimitiveNumber(Value value) {
        try {
            return value.fitsInByte()
                || value.fitsInShort()
                || value.fitsInInt()
                || value.fitsInLong()
                || value.fitsInFloat()
                || value.fitsInDouble();
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    private static Value memberSetter(Context context) {
        return ContextHolder.helper(context, PUT_MEMBER, PUT_MEMBER_SOURCE);
    }

    private static Value asyncMemberFactory(Context context) {
        return ContextHolder.helper(context, ASYNC_MEMBER_VALUE, ASYNC_MEMBER_VALUE_SOURCE);
    }

    /**
     * Adapter invoked from Python async member facades.
     */
    public static final class AsyncMemberAdapter {
        private AsyncMemberAdapter() {
        }

        /**
         * Adapt host values returned from Java members to values Python async code can consume.
         *
         * @param context The target Python context.
         * @param value The host value.
         * @return The adapted value.
         */
        public @Nullable Object adapt(Context context, @Nullable Object value) {
            Value awaitable = adaptAwaitable(context, value);
            if (awaitable != null) {
                return awaitable;
            }
            return value;
        }

        /**
         * Adapt a host async value returned from a Java member to a Python awaitable.
         *
         * @param context The target Python context.
         * @param value The host value.
         * @return The adapted Python awaitable, or null when the value is not async.
         */
        public @Nullable Value adaptAwaitable(Context context, @Nullable Object value) {
            if (value instanceof CompletionStage<?> completionStage) {
                return PythonAsyncioRuntime.toAwaitable(context, completionStage);
            }
            CompletionStage<?> publisherStage = publisherStage(value);
            if (publisherStage != null) {
                return PythonAsyncioRuntime.toAwaitable(context, publisherStage);
            }
            if (value instanceof Value polyglotValue) {
                if (polyglotValue.isHostObject()) {
                    Object hostObject = polyglotValue.asHostObject();
                    if (hostObject instanceof CompletionStage<?> completionStage) {
                        return PythonAsyncioRuntime.toAwaitable(context, completionStage);
                    }
                    CompletionStage<?> hostPublisherStage = publisherStage(hostObject);
                    if (hostPublisherStage != null) {
                        return PythonAsyncioRuntime.toAwaitable(context, hostPublisherStage);
                    }
                }
                try {
                    return PythonAsyncioRuntime.toAwaitable(context, polyglotValue.as(CompletionStage.class));
                } catch (RuntimeException e) {
                    // Fall through and return the original value.
                }
            }
            return null;
        }

        private static @Nullable CompletionStage<?> publisherStage(@Nullable Object value) {
            if (!Publishers.isConvertibleToPublisher(value)) {
                return null;
            }
            Publisher<?> publisher;
            try {
                publisher = Publishers.convertToPublisher(ConversionService.SHARED, value);
            } catch (RuntimeException e) {
                return null;
            }
            PythonAsyncioRuntime.PythonCompletableFuture future = new PythonAsyncioRuntime.PythonCompletableFuture();
            publisher.subscribe(new ScalarPublisherSubscriber(future));
            return future;
        }
    }

    /**
     * Scalar reactive await bridge. It requests a single item, completes with the first value, and cancels upstream.
     */
    private static final class ScalarPublisherSubscriber implements Subscriber<Object> {
        private final PythonAsyncioRuntime.PythonCompletableFuture future;
        private final AtomicReference<Subscription> subscription = new AtomicReference<>();
        private final AtomicBoolean done = new AtomicBoolean();

        private ScalarPublisherSubscriber(PythonAsyncioRuntime.PythonCompletableFuture future) {
            this.future = future;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            if (!this.subscription.compareAndSet(null, subscription)) {
                subscription.cancel();
                return;
            }
            future.setCancelCallback(subscription::cancel);
            if (future.isCancelled()) {
                subscription.cancel();
            } else {
                subscription.request(1);
            }
        }

        @Override
        public void onNext(Object value) {
            if (done.compareAndSet(false, true)) {
                future.complete(value);
                Subscription current = subscription.get();
                if (current != null) {
                    current.cancel();
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (done.compareAndSet(false, true)) {
                future.completeExceptionally(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (done.compareAndSet(false, true)) {
                future.complete(null);
            }
        }
    }

    /**
     * Copy simple and host-backed Python instance members into another context.
     *
     * @param source The source Python object.
     * @param target The target Python object.
     */
    public static void copyTransferableMembers(Value source, Value target) {
        if (source == null || target == null || isNone(source) || isNone(target) || !source.hasMembers()) {
            return;
        }
        for (String key : transferableMemberNames(source)) {
            if (key.startsWith("__")) {
                continue;
            }
            Value member = source.getMember(key);
            Object transferable = transferableMember(member);
            if (transferable != null) {
                putMember(target, key, transferable);
            }
        }
    }

    private static List<String> transferableMemberNames(Value source) {
        Value names = ContextHolder.helper(source.getContext(), TRANSFERABLE_MEMBER_NAMES, TRANSFERABLE_MEMBER_NAMES_SOURCE);
        Value result = names.execute(source);
        List<String> keys = new ArrayList<>();
        if (result.hasArrayElements()) {
            for (long i = 0; i < result.getArraySize(); i++) {
                keys.add(result.getArrayElement(i).asString());
            }
        }
        return keys;
    }

    private static @Nullable Object transferableMember(@Nullable Value member) {
        if (member == null || isNone(member)) {
            return null;
        }
        if (member.isHostObject()) {
            return member.asHostObject();
        }
        if (member.isBoolean()) {
            return member.asBoolean();
        }
        if (member.isString()) {
            return member.asString();
        }
        if (isInteropPrimitiveNumber(member)) {
            return member.as(Object.class);
        }
        return null;
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
        Context context = receiver.getContext();
        ContextHolder.enterExecutionFrame(context);
        try {
            Value member = receiver.getMember(name);
            if (member == null) {
                throw new IllegalArgumentException("No Python member [" + name + "] found");
            }
            return ContextHolder.helper(context, INVOKE_METHOD, INVOKE_METHOD_SOURCE).execute(receiver, name, arguments);
        } finally {
            ContextHolder.exitExecutionFrame(context);
        }
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
        return ContextHolder.helper(context, RAW_CLASS_MEMBER, RAW_CLASS_MEMBER_SOURCE);
    }

    /**
     * Return a value as {@link Object} so generated code can perform unchecked generic casts.
     *
     * @param value The value
     * @return The value as an object
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T asObject(@Nullable Object value) {
        return (T) value;
    }

    /**
     * Unwraps a generated Python wrapper that crossed a polyglot boundary as a host or proxy object.
     *
     * @param value The source polyglot value
     * @param targetType The expected Java wrapper type
     * @return The existing host wrapper, or {@code null} when the value is not one
     */
    public static @Nullable Object unwrapHostObject(@Nullable Value value, Class<?> targetType) {
        return ValueCoercible.hostObject(value, targetType);
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
     * Convert a GraalPy Value representing a list using a generated element converter.
     *
     * @param graalValue the GraalPy Value (should be a list-like object)
     * @param converter the converter to apply to each element
     * @param <T> the expected list element type
     * @return a Java List with converted elements
     */
    public static <T> @Nullable List<T> convertList(Value graalValue, PolyglotValueConverter<T> converter) {
        if (isNone(graalValue)) {
            return null;
        }
        try {
            long size = getSize(graalValue);
            if (size == 0) {
                return List.of();
            }
            List<T> result = new ArrayList<>(Long.valueOf(size).intValue());
            for (long i = 0; i < size; i++) {
                Value elementValue = getElementAt(graalValue, i);
                if (elementValue != null) {
                    result.add(converter.convert(elementValue));
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Convert a Java list that may contain GraalPy values using a generated element converter.
     *
     * @param list the source list
     * @param converter the converter to apply to GraalPy elements
     * @param <T> the expected list element type
     * @return a Java List with converted elements
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable List<T> convertList(@Nullable List<?> list, PolyglotValueConverter<T> converter) {
        if (list == null) {
            return null;
        }
        List<T> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element == null) {
                result.add(null);
            } else if (element instanceof Value value) {
                result.add(converter.convert(value));
            } else {
                result.add((T) element);
            }
        }
        return result;
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
        T enumValue = convertEnumValue(value, targetType);
        if (enumValue != null) {
            return enumValue;
        }
        T mappedWrapper = convertMappedWrapper(value, targetType);
        if (mappedWrapper != null) {
            return mappedWrapper;
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
    @SuppressWarnings({"unchecked", "NullAway"})
    public static <T> HttpResponse<T> convertHttpResponse(Value value, Class<T> bodyType) {
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
        if (Object.class.equals(bodyType)) {
            @SuppressWarnings("unchecked")
            T converted = (T) convertObjectResponseBody(rawBody);
            return converted;
        }
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

    private static @Nullable Object convertObjectResponseBody(@Nullable Object rawBody) {
        if (rawBody == null) {
            return null;
        }
        if (rawBody instanceof ProxyObject proxyObject && proxyObject.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            ValueCoercible host = ValueCoercible.hostObject(proxyObject);
            if (host != null) {
                return host;
            }
        }
        if (rawBody instanceof Value value) {
            if (value.isHostObject()) {
                return convertObjectResponseBody(value.asHostObject());
            }
            ValueCoercible host = ValueCoercible.hostObject(value);
            if (host != null) {
                return host;
            }
            return value.as(Object.class);
        }
        return rawBody;
    }

    private static <T> @Nullable T convertMappedWrapper(Value value, Class<T> targetType) {
        try {
            ValueCoercible host = ValueCoercible.hostObject(value);
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
        ValueCoercible host = ValueCoercible.hostObject(proxyObject);
        if (host == null) {
            return null;
        }
        if (targetType.isInstance(host)) {
            return targetType.cast(host);
        }
        return convertValue(host.asPolyglotValue(), targetType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> @Nullable T convertEnumValue(Value value, Class<T> targetType) {
        if (!targetType.isEnum()) {
            return null;
        }
        String enumName = enumName(value);
        if (enumName == null) {
            return null;
        }
        try {
            return (T) Enum.valueOf((Class) targetType.asSubclass(Enum.class), enumName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static @Nullable String enumName(Value value) {
        if (value.isString()) {
            return value.asString();
        }
        String memberName = enumMemberString(value, "name");
        if (memberName != null) {
            return memberName;
        }
        String memberValue = enumMemberString(value, "value");
        if (memberValue != null) {
            return memberValue;
        }
        String stringValue = value.toString();
        int lastDot = stringValue.lastIndexOf('.');
        if (lastDot > -1 && lastDot < stringValue.length() - 1) {
            return stringValue.substring(lastDot + 1);
        }
        return null;
    }

    private static @Nullable String enumMemberString(Value value, String memberName) {
        if (!value.hasMembers() || !value.hasMember(memberName)) {
            return null;
        }
        Value memberValue = value.getMember(memberName);
        if (isNone(memberValue)) {
            return null;
        }
        if (memberValue.isString()) {
            return memberValue.asString();
        }
        return memberValue.toString();
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
