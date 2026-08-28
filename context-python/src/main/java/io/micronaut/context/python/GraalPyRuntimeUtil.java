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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
 * @since 5.2.0
 */
@Internal
@SuppressWarnings({"checkstyle:InnerTypeLast", "checkstyle:JavadocMethod", "checkstyle:TodoComment"})
@Experimental
public final class GraalPyRuntimeUtil {

    public static final String PYTHON = "python";
    private static final String TRANSFERABLE_MEMBER_NAMES = "__micronaut_transferable_member_names";
    private static final String PUT_MEMBER = "__micronaut_put_member";
    private static final String ASYNC_MEMBER_VALUE = "__micronaut_async_member_value";
    private static final String INVOKE_METHOD = "__micronaut_invoke_method";
    private static final String RAW_CLASS_MEMBER = "__micronaut_get_raw_class_member";
    private static final String TO_PYTHON_STANDARD_TYPE = "__micronaut_to_python_standard_type";
    private static final String UTC_OFFSET = "__micronaut_utc_offset";
    private static final AsyncMemberAdapter ASYNC_MEMBER_ADAPTER = new AsyncMemberAdapter();
    private static final Source PUT_MEMBER_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_put_member(target, name, value):
            setattr(target, name, value)
        """, "micronaut-put-member.py").cached(true).buildLiteral();
    private static final Source TO_PYTHON_STANDARD_TYPE_SOURCE = Source.newBuilder(PYTHON, """
        import datetime
        import uuid

        def __micronaut_to_python_standard_type(kind, value, nanos=0):
            if kind == "date":
                return datetime.date.fromisoformat(value)
            if kind == "time":
                return datetime.time.fromisoformat(value)
            if kind == "datetime":
                return datetime.datetime.fromisoformat(value)
            if kind == "duration":
                return datetime.timedelta(seconds=value, microseconds=nanos // 1000)
            if kind == "zone_offset":
                if value == "Z":
                    return datetime.timezone.utc
                return datetime.time.fromisoformat("00:00:00" + value).tzinfo
            if kind == "uuid":
                return uuid.UUID(value)
            raise ValueError("Unsupported Micronaut Python standard type: " + kind)
        """, "micronaut-to-python-standard-type.py").cached(true).buildLiteral();
    private static final Source UTC_OFFSET_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_utc_offset(value):
            return value.utcoffset(None)
        """, "micronaut-utc-offset.py").cached(true).buildLiteral();
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
    private static final String LEN = "__len__";
    private static final String ITER = "__iter__";
    private static final String NEXT = "__next__";
    private static final String GETITEM = "__getitem__";

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
        Object standardType = coerceStandardTypeToContext(value, context);
        if (standardType != value) {
            return standardType;
        }
        switch (value) {
            case null -> {
                return null;
            }
            case PooledValueCoercible pooledValueCoercible -> {
                return pooledValueCoercible.asPolyglotValue(context);
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
            case Object[] array -> {
                Object[] result = new Object[array.length];
                for (int i = 0; i < array.length; i++) {
                    result[i] = coerceToContext(array[i], context);
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
        return switch (value) {
            case PooledValueCoercible pooledValueCoercible ->
                pooledValueCoercible.asPolyglotValue(context);
            case List<?> _ when List.class.equals(declaredType) ->
                coerceToContext(value, context);
            case Map<?, ?> _ when Map.class.equals(declaredType) ->
                coerceToContext(value, context);
            case Set<?> _ when Set.class.equals(declaredType) ->
                coerceToContext(value, context);
            case Object[] _ when declaredType.isArray() -> coerceToContext(value, context);
            default -> value;
        };
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
        return PythonContextRuntime.helper(context, TO_PYTHON_STANDARD_TYPE, TO_PYTHON_STANDARD_TYPE_SOURCE);
    }

    static boolean isPythonType(Value value, String module, String typeName) {
        if (value == null || value.isNull() || !value.hasMembers()) {
            return false;
        }
        Value type = value.getMember("__class__");
        return type != null && type.hasMembers()
            && module.equals(stringMember(type, "__module__"))
            && typeName.equals(stringMember(type, "__name__"));
    }

    static LocalDate convertLocalDate(Value value) {
        return LocalDate.parse(value.invokeMember("isoformat").asString());
    }

    static LocalTime convertLocalTime(Value value) {
        rejectAware(value, "time");
        return LocalTime.parse(value.invokeMember("isoformat").asString());
    }

    static LocalDateTime convertLocalDateTime(Value value) {
        rejectAware(value, "datetime");
        return LocalDateTime.parse(value.invokeMember("isoformat").asString());
    }

    static Duration convertDuration(Value value) {
        long days = value.getMember("days").asLong();
        long seconds = value.getMember("seconds").asLong();
        long microseconds = value.getMember("microseconds").asLong();
        return Duration.ofDays(days).plusSeconds(seconds).plusNanos(Math.multiplyExact(microseconds, 1_000));
    }

    static ZoneOffset convertZoneOffset(Value value) {
        if (!isPythonType(value, "datetime", "timezone")) {
            throw new IllegalArgumentException("Only fixed-offset datetime.timezone values can be converted to ZoneOffset");
        }
        Value offsetValue = PythonContextRuntime.helper(value.getContext(), UTC_OFFSET, UTC_OFFSET_SOURCE).execute(value);
        Duration offset = convertDuration(offsetValue);
        if (offset.getNano() != 0) {
            throw new IllegalArgumentException("datetime.timezone offset must be an exact number of seconds");
        }
        return ZoneOffset.ofTotalSeconds(Math.toIntExact(offset.getSeconds()));
    }

    static UUID convertUuid(Value value) {
        return UUID.fromString(value.invokeMember("__str__").asString());
    }

    private static void rejectAware(Value value, String typeName) {
        Value tzinfo = value.getMember("tzinfo");
        if (tzinfo != null && !isNone(tzinfo)) {
            throw new IllegalArgumentException("Aware datetime." + typeName + " values cannot be converted to a naive Java type");
        }
    }

    private static @Nullable String stringMember(Value value, String name) {
        Value member = value.getMember(name);
        return member == null || member.isNull() ? null : member.asString();
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
        return PythonContextRuntime.helper(context, PUT_MEMBER, PUT_MEMBER_SOURCE);
    }

    private static Value asyncMemberFactory(Context context) {
        return PythonContextRuntime.helper(context, ASYNC_MEMBER_VALUE, ASYNC_MEMBER_VALUE_SOURCE);
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

    /**
     * Copy simple and host-backed Python instance members into another context.
     *
     * @param source The source Python object.
     * @param target The target Python object.
     */
    public static void copyTransferableMembers(@Nullable Value source, @Nullable Value target) {
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
        Value names = PythonContextRuntime.helper(source.getContext(), TRANSFERABLE_MEMBER_NAMES, TRANSFERABLE_MEMBER_NAMES_SOURCE);
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
     * <p>
     * Generated Java stubs use this method instead of calling {@link Value#invokeMember(String, Object...)}
     * directly so every Python call enters the {@link PythonContextRuntime} execution tracker for the
     * receiver's actual context. The execution frame keeps graceful shutdown and pooled-context cleanup
     * from observing the context as idle while a bridge invocation, or nested bridge invocation, is still
     * unwinding.
     * <p>
     * Invocation is delegated to a context-local Python helper because Python method lookup is not just
     * a map lookup. The helper first uses {@code getattr} for normal bound-method behavior, then falls
     * back to walking the class {@code __mro__} and applying {@code __get__} so descriptors and inherited
     * methods are invoked with Python semantics before the result crosses back to Java.
     *
     * @param receiver The Python receiver
     * @param name The method name
     * @param arguments The method arguments
     * @return The invocation result
     */
    public static Value invokePythonMethod(Value receiver, String name, Object[] arguments) {
        Context context = receiver.getContext();
        return PythonContextRuntime.withExecutionFrame(context, () -> {
            Value member = receiver.getMember(name);
            if (member == null) {
                throw new IllegalArgumentException("No Python member [" + name + "] found");
            }
            return PythonContextRuntime.helper(context, INVOKE_METHOD, INVOKE_METHOD_SOURCE).execute(receiver, name, arguments);
        });
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
        return PythonContextRuntime.helper(context, RAW_CLASS_MEMBER, RAW_CLASS_MEMBER_SOURCE);
    }

    /**
     * Return a value as {@link Object} so generated code can perform unchecked generic casts.
     *
     * @param <T> The target object type
     * @param value The value
     * @return The value as an object
     */
    @SuppressWarnings("unchecked")
    public static <T> @Nullable T asObject(@Nullable Object value) {
        return (T) value;
    }

    /**
     * Convert a GraalPy value to a general Java object while preserving host objects.
     *
     * @param value The source polyglot value
     * @return The converted object
     */
    public static @Nullable Object convertObject(@Nullable Value value) {
        return convertObjectResponseBody(value);
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
                Value iterator = keysValue.invokeMember(ITER);
                while (true) {
                    try {
                        Value nextValue = iterator.invokeMember(NEXT);
                        if (nextValue == null || nextValue.isNull()) {
                            break;
                        }
                        try {
                            K convertedKey = convertValue(nextValue, keyType);
                            Value mapValue = graalValue.invokeMember(GETITEM, nextValue);
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
    public static <T> Optional<T> convertOptional(Value graalValue, Class<T> elementType) {
        if (isNone(graalValue)) {
            return Optional.empty();
        }
        if (graalValue.isHostObject()) {
            Object hostObject = graalValue.asHostObject();
            if (hostObject instanceof Optional<?> optional) {
                if (optional.isEmpty()) {
                    return Optional.empty();
                }
                Object optionalValue = optional.get();
                if (elementType.isInstance(optionalValue)) {
                    return Optional.of(elementType.cast(optionalValue));
                }
                if (optionalValue instanceof Value value) {
                    T convertedValue = convertValue(value, elementType);
                    return convertedValue == null ? Optional.empty() : Optional.of(convertedValue);
                }
            }
        }

        // Convert the value and wrap in Optional
        T convertedValue = convertValue(graalValue, elementType);
        if (convertedValue == null) {
            return Optional.empty();
        }

        return Optional.of(convertedValue);
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
        // A custom Set implementation that doesn't create a new map would be better here.
        if (isNone(graalValue)) {
            return null;
        }

        Set<@Nullable T> result = new HashSet<>();

        try {
            // Try to iterate directly over the set
            if (graalValue.hasIterator()) {
                Value iterator = graalValue.invokeMember(ITER);
                while (true) {
                    try {
                        Value nextValue = iterator.invokeMember(NEXT);
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
                return new HashSet<>();
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
     * Convert each item emitted by a Python-returned publisher to the declared Java item type.
     *
     * @param publisher The source publisher
     * @param itemType The declared publisher item type
     * @param <T> The item type
     * @return The converted publisher
     */
    @SuppressWarnings("unchecked")
    public static <T> Publisher<T> convertPublisher(Publisher<?> publisher, Class<T> itemType) {
        return Publishers.map((Publisher<Object>) publisher, item -> convertPublishedValue(item, itemType));
    }

    /**
     * Convert a GraalPy Value representing a publisher to a typed Java publisher.
     *
     * @param value The source polyglot publisher
     * @param itemType The declared publisher item type
     * @param <T> The item type
     * @return The converted publisher
     */
    public static <T> @Nullable Publisher<T> convertPublisher(Value value, Class<T> itemType) {
        Publisher<?> publisher = convertValue(value, Publisher.class);
        if (publisher == null) {
            return null;
        }
        return convertPublisher(publisher, itemType);
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
            return mutableResponse.body(convertedBody);
        }
        return (HttpResponse<T>) response;
    }

    private static <T> @Nullable T convertPublishedValue(@Nullable Object item, Class<T> itemType) {
        if (item == null || itemType.isInstance(item)) {
            return itemType.cast(item);
        }
        switch (item) {
            case HttpResponse<?> response when HttpResponse.class.isAssignableFrom(itemType) -> {
                return itemType.cast(convertHttpResponse(response, Object.class));
            }
            case Value value -> {
                return convertValue(value, itemType);
            }
            default -> {
                try {
                    return convertValue(Value.asValue(item), itemType);
                } catch (ClassCastException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException e) {
                    return itemType.cast(item);
                }
            }
        }
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

    /**
     * Convert a Python enum value to the string representation exposed by Python.
     *
     * @param value The Python enum value
     * @return The enum string value
     */
    @SuppressWarnings("unused")
    @UsedByGeneratedCode
    public static String enumStringValue(@Nullable Value value) {
        if (value == null) {
            return "null";
        }
        Value target = value;
        if (value.hasMembers() && value.hasMember(AnnotationMetadata.VALUE_MEMBER)) {
            target = value.getMember(AnnotationMetadata.VALUE_MEMBER);
        }
        if (isNone(target)) {
            return "null";
        }
        if (target.isString()) {
            return target.asString();
        }
        return target.toString();
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
            for (Enum<?> enumConstant : targetType.asSubclass(Enum.class).getEnumConstants()) {
                if (enumName.equals(enumConstant.toString())) {
                    return (T) enumConstant;
                }
            }
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
            if (value.canInvokeMember(LEN)) {
                Value length = value.invokeMember(LEN);
                return length.asLong();
            }

            // Try len() function
            if (value.canInvokeMember(LEN)) {
                Value length = value.invokeMember(LEN);
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
                Value iterator = value.invokeMember(ITER);
                while (true) {
                    try {
                        iterator.invokeMember(NEXT);
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
            if (collection.canInvokeMember(GETITEM)) {
                return collection.invokeMember(GETITEM, index);
            }

            // Try iteration to specific index
            if (collection.hasIterator()) {
                Value iterator = collection.invokeMember(ITER);
                for (long i = 0; i <= index; i++) {
                    Value item = iterator.invokeMember(NEXT);
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
