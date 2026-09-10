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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import java.lang.reflect.Array;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Coercion of Java values into a Python context: standard types, generated wrappers, pooled values and
 * the members they expose, including the adaptation of async Java members for Python callers.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonCoercion {

    private static final String TRANSFERABLE_MEMBER_NAMES = "__micronaut_transferable_member_names";

    private static final String PUT_MEMBER = "__micronaut_put_member";

    private static final String ASYNC_MEMBER_VALUE = "__micronaut_async_member_value";

    private static final String TO_PYTHON_STANDARD_TYPE = "__micronaut_to_python_standard_type";

    private static final AsyncMemberAdapter ASYNC_MEMBER_ADAPTER = new AsyncMemberAdapter();

    private static final ScopedValue<ContextConversion> CURRENT_CONTEXT_CONVERSION = ScopedValue.newInstance();

    private PythonCoercion() {
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
        return switch (value) {
            case ValueCoercible valueCoercible when !(value instanceof PooledValueCoercible) ->
                valueCoercible.asPolyglotValue();
            case null, default -> value;
        };
    }

    /**
     * Coerce values passed into a target Python context.
     *
     * @param value The value to coerce
     * @param context The target context
     * @return The coerced value
     */
    public static @Nullable Object coerceToContext(@Nullable Object value, Context context) {
        if (isInteropPrimitive(value)) {
            return value;
        }
        return withContextConversion(context, () -> coerceToContext0(value, context));
    }

    private static @Nullable Object coerceToContext0(@Nullable Object value, Context context) {
        Object standardType = coerceStandardTypeToContext(value, context);
        if (standardType != value) {
            return standardType;
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            Object[] result = new Object[length];
            for (int i = 0; i < length; i++) {
                result[i] = coerceToContext(Array.get(value, i), context);
            }
            return result;
        }
        switch (value) {
            case null -> {
                return null;
            }
            case PooledValueCoercible pooledValueCoercible -> {
                return coercePooledValue(pooledValueCoercible, context);
            }
            case ValueCoercible valueCoercible -> {
                Value polyglotValue = valueCoercible.asPolyglotValue();
                if (polyglotValue == null || isValueInContext(polyglotValue, context)) {
                    return polyglotValue;
                }
                throw new IllegalArgumentException(
                    "Python wrapper " + value.getClass().getName() + " cannot be reconstructed in the target context"
                );
            }
            case Value polyglotValue -> {
                if (isValueInContext(polyglotValue, context)) {
                    return polyglotValue;
                }
                throw new IllegalArgumentException("Cannot pass a polyglot Value to a different context");
            }
            case List<?> list -> {
                List<@Nullable Object> result = new ArrayList<>(list.size());
                for (Object element : list) {
                    result.add(coerceToContext(element, context));
                }
                return result;
            }
            case Map<?, ?> map -> {
                Map<Object, Object> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(
                        coerceToContext(entry.getKey(), context),
                        coerceToContext(entry.getValue(), context)
                    );
                }
                return result;
            }
            case Set<?> set -> {
                Set<@Nullable Object> result = new HashSet<>();
                for (Object element : set) {
                    result.add(coerceToContext(element, context));
                }
                return result;
            }
            default -> {
            }
        }
        return value;
    }

    /**
     * Coerce a value using the generated Java bridge's declared parameter type.
     * Some host objects implement collection interfaces as an implementation
     * detail and should stay host objects unless the Python method declares the
     * plain collection contract.
     *
     * @param value The value to coerce
     * @param context The target context
     * @param declaredType The declared Java bridge parameter type
     * @return The coerced value
     */
    public static @Nullable Object coerceToContext(@Nullable Object value, Context context, Class<?> declaredType) {
        if (isInteropPrimitive(value)) {
            return value;
        }
        return withContextConversion(context, () -> coerceToContext0(value, context, declaredType));
    }

    private static @Nullable Object coerceToContext0(@Nullable Object value, Context context, Class<?> declaredType) {
        if (value == null) {
            return null;
        }
        if (declaredType == null) {
            return coerceToContext(value, context);
        }
        Object standardType = coerceStandardTypeToContext(value, context);
        if (standardType != value) {
            return standardType;
        }
        if (declaredType.isArray() && value.getClass().isArray()) {
            return coerceToContext(value, context);
        }
        return switch (value) {
            case PooledValueCoercible pooledValueCoercible ->
                coercePooledValue(pooledValueCoercible, context);
            case ValueCoercible _, Value _ -> coerceToContext0(value, context);
            case List<?> _ when List.class.equals(declaredType) ->
                coerceToContext(value, context);
            case Map<?, ?> _ when Map.class.equals(declaredType) ->
                coerceToContext(value, context);
            case Set<?> _ when Set.class.equals(declaredType) ->
                coerceToContext(value, context);
            default -> value;
        };
    }

    /**
     * Determines whether a polyglot value belongs to the supplied context.
     *
     * @param value The value to inspect
     * @param context The expected context
     * @return Whether the value belongs to the context
     */
    @UsedByGeneratedCode
    public static boolean isValueInContext(@Nullable Value value, Context context) {
        return value != null && context.equals(value.getContext());
    }

    /**
     * Converts a generated wrapper while preserving wrapper identity for the current conversion.
     *
     * @param value The generated wrapper
     * @param context The target context
     * @return The context-local Python value
     */
    @UsedByGeneratedCode
    public static Value coercePooledValue(PooledValueCoercible value, Context context) {
        return withContextConversion(context, () -> {
            ContextConversion conversion = CURRENT_CONTEXT_CONVERSION.get();
            Value existing = conversion.get(value);
            if (existing != null) {
                return existing;
            }
            if (!conversion.begin(value)) {
                throw new IllegalStateException(
                    "Cyclic Python wrapper cannot be reconstructed before its target instance is allocated: "
                        + value.getClass().getName()
                );
            }
            try {
                Value reconstructed = value.reconstructPolyglotValue(context);
                conversion.remember(value, reconstructed);
                return reconstructed;
            } finally {
                conversion.end(value);
            }
        });
    }

    /**
     * Registers a newly allocated Python object before generated code populates its properties.
     *
     * @param source The source wrapper
     * @param context The target context
     * @param value The newly allocated value
     */
    @UsedByGeneratedCode
    public static void rememberPooledValue(PooledValueCoercible source, Context context, Value value) {
        if (!CURRENT_CONTEXT_CONVERSION.isBound()
            || !CURRENT_CONTEXT_CONVERSION.get().context.equals(context)
            || !value.getContext().equals(context)) {
            throw new IllegalStateException("No matching Python context conversion is active");
        }
        CURRENT_CONTEXT_CONVERSION.get().remember(source, value);
    }

    private static <T> T withContextConversion(Context context, Supplier<T> operation) {
        if (CURRENT_CONTEXT_CONVERSION.isBound() && CURRENT_CONTEXT_CONVERSION.get().context.equals(context)) {
            return operation.get();
        }
        return ScopedValue.where(CURRENT_CONTEXT_CONVERSION, new ContextConversion(context)).call(operation::get);
    }

    private static @Nullable Object coerceStandardTypeToContext(@Nullable Object value, Context context) {
        return switch (value) {
            case null -> null;
            case LocalDate localDate ->
                standardTypeHelper(context).execute("date", localDate.toString());
            case LocalTime localTime ->
                standardTypeHelper(context).execute("time", localTime.toString());
            case LocalDateTime localDateTime ->
                standardTypeHelper(context).execute("datetime", localDateTime.toString());
            case Duration duration ->
                standardTypeHelper(context).execute("duration", duration.getSeconds(), duration.getNano());
            case ZoneOffset zoneOffset ->
                standardTypeHelper(context).execute("zone_offset", zoneOffset.getId(), 0);
            case UUID uuid -> standardTypeHelper(context).execute("uuid", uuid.toString());
            default -> value;
        };
    }

    private static Value standardTypeHelper(Context context) {
        return PythonContextRuntime.helper(context, TO_PYTHON_STANDARD_TYPE);
    }

    static boolean isPythonType(Value value, String module, String typeName) {
        if (value == null || value.isNull() || !value.hasMembers()) {
            return false;
        }
        Value type = value.getMember("__class__");
        return type != null && type.hasMembers()
            && module.equals(PythonConversion.stringMember(type, "__module__"))
            && typeName.equals(PythonConversion.stringMember(type, "__name__"));
    }

    /**
     * Coerce arguments passed into a target Python context.
     *
     * @param context The target context
     * @param args The arguments
     * @return The coerced arguments
     */
    public static Object[] coerceArgumentsToContext(Context context, Object[] args) {
        boolean conversionRequired = false;
        for (Object arg : args) {
            if (!isInteropPrimitive(arg)) {
                conversionRequired = true;
                break;
            }
        }
        if (!conversionRequired) {
            return args;
        }
        return withContextConversion(context, () -> {
            Object[] result = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                result[i] = coerceToContext0(args[i], context);
            }
            return result;
        });
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
     * Assign several members on a Python value in a single guest call.
     *
     * <p>Rebuilding a dataclass in a pooled context writes every field; doing that with one helper
     * invocation instead of one per field removes most of the per-field interop cost.</p>
     *
     * @param target The Python object to update
     * @param names The member names
     * @param values The member values, positionally matching {@code names}
     */
    @UsedByGeneratedCode
    public static void putMembers(Value target, String[] names, @Nullable Object[] values) {
        if (names.length != values.length) {
            throw new IllegalArgumentException("Member names and values differ in length");
        }
        if (names.length == 0) {
            return;
        }
        Context context = target.getContext();
        PythonContextRuntime.propertiesSetter(context).executeVoid(target, names, coerceArgumentsToContext(context, values));
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
        if (value instanceof PooledValueCoercible || value instanceof Value) {
            return coerceToContext(value, context);
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
        if (!value.isNumber()) {
            return false;
        }
        return value.fitsInByte()
            || value.fitsInShort()
            || value.fitsInInt()
            || value.fitsInLong()
            || value.fitsInFloat()
            || value.fitsInDouble();
    }

    private static Value memberSetter(Context context) {
        return PythonContextRuntime.helper(context, PUT_MEMBER);
    }

    private static Value asyncMemberFactory(Context context) {
        return PythonContextRuntime.helper(context, ASYNC_MEMBER_VALUE);
    }

    /**
     * Copy simple and host-backed Python instance members into another context.
     *
     * @param source The source Python object.
     * @param target The target Python object.
     */
    public static void copyTransferableMembers(@Nullable Value source, @Nullable Value target) {
        if (source == null || target == null || PythonConversion.isNone(source) || PythonConversion.isNone(target) || !source.hasMembers()) {
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
        Value names = PythonContextRuntime.helper(source.getContext(), TRANSFERABLE_MEMBER_NAMES);
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
        if (member == null || PythonConversion.isNone(member)) {
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
     * Scalar reactive await bridge. It requests a single item, completes with the first value, and cancels upstream.
     */
    private static final class ScalarPublisherSubscriber implements Subscriber<Object> {
        private final PythonAsyncioRuntime.PythonCompletableFuture future;
        private final AtomicReference<@Nullable Subscription> subscription = new AtomicReference<>();
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

    private static final class ContextConversion {
        private final Context context;
        private @Nullable IdentityHashMap<PooledValueCoercible, Value> values;
        private @Nullable IdentityHashMap<PooledValueCoercible, Boolean> inProgress;

        private ContextConversion(Context context) {
            this.context = context;
        }

        private @Nullable Value get(PooledValueCoercible value) {
            return values == null ? null : values.get(value);
        }

        private void remember(PooledValueCoercible source, Value value) {
            if (values == null) {
                values = new IdentityHashMap<>();
            }
            values.put(source, value);
        }

        private boolean begin(PooledValueCoercible value) {
            if (inProgress == null) {
                inProgress = new IdentityHashMap<>();
            }
            return inProgress.put(value, Boolean.TRUE) == null;
        }

        private void end(PooledValueCoercible value) {
            if (inProgress != null) {
                inProgress.remove(value);
            }
        }
    }

    /**
     * Adapter invoked from Python async member facades.
     */
    @Experimental
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
}
