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

import io.micronaut.aop.InterceptedProxy;
import io.micronaut.context.python.annotation.PythonClass;
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
import java.util.Collection;
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
import org.graalvm.polyglot.proxy.ProxyExecutable;
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

    private static final String PYTHON_LIST = "__micronaut_python_list";

    private static final String PYTHON_DICT = "__micronaut_python_dict";

    private static final String ASYNC_MEMBER_VALUE = "__micronaut_async_member_value";

    private static final String TO_PYTHON_STANDARD_TYPE = "__micronaut_to_python_standard_type";

    private static final String SCOPED_PROXY_FACTORY = "__micronaut_create_scoped_proxy";

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
            case InterceptedProxy<?> proxy when isPythonInterfaceProxy(proxy) -> interceptedTargetValue(proxy);
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
            if (value instanceof byte[]) {
                // a byte[] payload is already an interop array; keeping it lets Python pass it on to
                // Java byte[] parameters and overloads unchanged
                return value;
            }
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
            case InterceptedProxy<?> proxy when isPythonInterfaceProxy(proxy) -> {
                return interceptedTargetValue(proxy);
            }
            case List<?> list -> {
                return coerceCollectionToContext(list, context);
            }
            case Map<?, ?> map -> {
                return coerceCollectionToContext(map, context);
            }
            case Set<?> set -> {
                return coerceCollectionToContext(set, context);
            }
            default -> {
            }
        }
        return value;
    }

    /**
     * Coerces a Java collection for a Python context.
     *
     * <p>A view of a Python collection ({@link PythonCollectionView}, or the Java view GraalPy returns
     * from {@link Value#as(Class)}) of the target context is the Python collection itself, and a
     * {@link PythonCollectionView} of another context is copied into a native collection. A plain JDK
     * collection (a {@code java.util} implementation such as {@link ArrayList} or {@link HashMap},
     * including the unmodifiable ones) is copied with coerced elements, so Python receives a mutable
     * collection of Python objects and never mutates Java state it was merely handed. A collection of
     * any other class, for example a cache or a view that implements {@link Map}, is passed by
     * reference: it keeps its identity and its API, and is never iterated or copied.</p>
     */
    private static @Nullable Object coerceCollectionToContext(Object collection, Context context) {
        if (collection instanceof PythonCollectionView view) {
            // a Python-owned collection stays a native Python collection in another context as well
            return view.isIn(context) ? view.pythonValue() : pythonCollectionElement(view, context);
        }
        if (isGuestBackedCollection(collection)) {
            Value guest = Value.asValue(collection);
            if (isValueInContext(guest, context)) {
                // a view of a Python collection of this context: hand the collection itself back
                return guest;
            }
            return copyCollection(collection, context);
        }
        return isPlainCollection(collection) ? copyCollection(collection, context) : collection;
    }

    private static Object copyCollection(Object collection, Context context) {
        return switch (collection) {
            case List<?> list -> {
                List<@Nullable Object> result = new ArrayList<>(list.size());
                for (Object element : list) {
                    result.add(coerceToContext(element, context));
                }
                yield result;
            }
            case Map<?, ?> map -> {
                Map<Object, Object> result = new HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    result.put(
                        coerceToContext(entry.getKey(), context),
                        coerceToContext(entry.getValue(), context)
                    );
                }
                yield result;
            }
            case Set<?> set -> {
                Set<@Nullable Object> result = new HashSet<>();
                for (Object element : set) {
                    result.add(coerceToContext(element, context));
                }
                yield result;
            }
            default -> collection;
        };
    }

    private static boolean isPlainCollection(Object collection) {
        return collection.getClass().getName().startsWith("java.util.");
    }

    /**
     * The Java view of a {@code list} attribute of a Python object, for the generated field of the
     * property: the Python list stays the attribute and the source of truth, reads and writes through
     * the returned list reach it. A Java list assigned to the attribute is returned as it is, and an
     * iterable that is not a list is converted the way {@link PythonConversion#convertList} does.
     *
     * @param member The attribute value
     * @param elementType The declared element type
     * @param <E> The element type
     * @return The list, or {@code null} for {@code None}
     */
    @UsedByGeneratedCode
    @SuppressWarnings("unchecked")
    public static <E> @Nullable List<E> listView(Value member, Class<E> elementType) {
        if (PythonConversion.isNone(member)) {
            return null;
        }
        if (member.isHostObject() && member.asHostObject() instanceof List<?> list) {
            return (List<E>) list;
        }
        if (member.hasArrayElements()) {
            return new PythonListView<>(member, elementType);
        }
        return PythonConversion.convertList(member, elementType);
    }

    /**
     * The Java view of a {@code dict} attribute of a Python object, for the generated field of the
     * property, as {@link #listView} for a list.
     *
     * @param member The attribute value
     * @param keyType The declared key type
     * @param valueType The declared value type
     * @param <K> The key type
     * @param <V> The value type
     * @return The map, or {@code null} for {@code None}
     */
    @UsedByGeneratedCode
    @SuppressWarnings("unchecked")
    public static <K, V> @Nullable Map<K, V> mapView(Value member, Class<K> keyType, Class<V> valueType) {
        if (PythonConversion.isNone(member)) {
            return null;
        }
        if (member.isHostObject() && member.asHostObject() instanceof Map<?, ?> map) {
            return (Map<K, V>) map;
        }
        if (member.hasHashEntries()) {
            return new PythonMapView<>(member, keyType, valueType);
        }
        return PythonConversion.convertMap(member, keyType, valueType);
    }

    /**
     * Writes the list held by the generated field of a property to the attribute of the Python object
     * and returns the field value to hold from then on. A view of the attribute of this context is
     * already current. Any other Java list is copied into a new Python list, so the attribute keeps
     * its native type, and the view of that list becomes the field value: the collection assigned
     * from Java is detached from that point. A collection class of its own (not a JDK one) is passed
     * by reference, as an argument would be.
     *
     * @param target The Python object
     * @param name The attribute name
     * @param value The field value
     * @param elementType The declared element type
     * @return The list to keep in the field
     */
    @UsedByGeneratedCode
    public static @Nullable List<?> putListMember(Value target, String name, @Nullable List<?> value, Class<?> elementType) {
        Context context = target.getContext();
        if (value == null || isAssignedAsIs(value, context)) {
            putMember(target, name, value);
            return value;
        }
        memberSetter(context).executeVoid(target, name, pythonList(value, context));
        return listView(target.getMember(name), elementType);
    }

    /**
     * Writes the map held by the generated field of a property to the attribute of the Python object
     * and returns the field value to hold from then on, as {@link #putListMember} for a list.
     *
     * @param target The Python object
     * @param name The attribute name
     * @param value The field value
     * @param keyType The declared key type
     * @param valueType The declared value type
     * @return The map to keep in the field
     */
    @UsedByGeneratedCode
    public static @Nullable Map<?, ?> putMapMember(Value target, String name, @Nullable Map<?, ?> value, Class<?> keyType, Class<?> valueType) {
        Context context = target.getContext();
        if (value == null || isAssignedAsIs(value, context)) {
            putMember(target, name, value);
            return value;
        }
        memberSetter(context).executeVoid(target, name, pythonDict(value, context));
        return mapView(target.getMember(name), keyType, valueType);
    }

    /**
     * Whether a field value is assigned to the Python attribute as it is: a view of a collection of
     * the target context, or a collection class of its own that is passed by reference. A plain JDK
     * collection, or a view of another context, is copied into a native Python collection.
     */
    private static boolean isAssignedAsIs(Object value, Context context) {
        if (value instanceof PythonCollectionView view) {
            return view.isIn(context);
        }
        return !isPlainCollection(value);
    }

    /**
     * Converts an element of a Python collection read through a view: a nested Python list or dict
     * requested as a plain {@link List} or {@link Map} is viewed in turn, so nested collections keep
     * the same semantics; every other element is converted to the declared type.
     */
    @SuppressWarnings("unchecked")
    static <T> @Nullable T viewElement(Value element, Class<T> targetType) {
        if (!element.isHostObject() && !element.isString()) {
            if (targetType == List.class && element.hasArrayElements()) {
                return (T) new PythonListView<>(element, Object.class);
            }
            if (targetType == Map.class && element.hasHashEntries()) {
                return (T) new PythonMapView<>(element, Object.class, Object.class);
            }
        }
        return PythonConversion.convertValue(element, targetType);
    }

    /**
     * A new Python list with the coerced elements of a Java list; nested plain lists and maps become
     * Python lists and dicts as well.
     */
    private static Value pythonList(List<?> list, Context context) {
        Object[] items = new Object[list.size()];
        int i = 0;
        for (Object element : list) {
            items[i++] = pythonCollectionElement(element, context);
        }
        return PythonContextRuntime.helper(context, PYTHON_LIST).execute((Object) items);
    }

    private static Value pythonDict(Map<?, ?> map, Context context) {
        Object[] keys = new Object[map.size()];
        Object[] values = new Object[map.size()];
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            keys[i] = pythonCollectionElement(entry.getKey(), context);
            values[i++] = pythonCollectionElement(entry.getValue(), context);
        }
        return PythonContextRuntime.helper(context, PYTHON_DICT).execute(keys, values);
    }

    /**
     * Coerces a value written through a view into a Python collection: a plain Java list or map
     * becomes a Python list or dict, so the Python collection holds Python values only.
     *
     * @param value The value
     * @param context The context of the Python collection
     * @return The coerced value
     */
    static @Nullable Object viewValue(@Nullable Object value, Context context) {
        return pythonCollectionElement(value, context);
    }

    private static @Nullable Object pythonCollectionElement(@Nullable Object element, Context context) {
        if (element instanceof List<?> list && (isPlainCollection(list) || list instanceof PythonCollectionView)) {
            return pythonList(list, context);
        }
        if (element instanceof Map<?, ?> map && (isPlainCollection(map) || map instanceof PythonCollectionView)) {
            return pythonDict(map, context);
        }
        return coerceToContext(element, context);
    }

    /**
     * Whether a collection is the Java view of a Python collection, as returned by
     * {@link Value#as(Class)} for {@link List}, {@link Map} and {@link Set} targets.
     */
    private static boolean isGuestBackedCollection(Object collection) {
        return collection.getClass().getName().startsWith("com.oracle.truffle.polyglot.");
    }

    /**
     * Coerce a value using the generated Java bridge's declared parameter type.
     * A collection argument is only rebuilt with coerced elements when the Python
     * method declares the plain collection contract; a parameter declared with a
     * more specific type (a cache, a view) always stays the host object it is.
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
            case InterceptedProxy<?> proxy when isPythonInterfaceProxy(proxy) -> interceptedTargetValue(proxy);
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
     * The Python object standing in for an AOP proxy of a Python class.
     * <p>
     * A generated proxy of a Python class (the scoped proxy of a {@code @Refreshable} factory bean, for
     * example) is a subclass of the generated stub, or an implementation of the generated interface, that
     * stands in for the bean its scope currently holds; it has no Python object of its own. Python code
     * receiving the proxy gets a Python scoped proxy of the same class instead: every attribute read, write
     * and method call is forwarded to the Python object of the bean the proxy resolves through its scope at
     * that moment, so a refreshed or replaced bean is seen by Python callers the way Java callers see it.
     * The Python proxy is created once per proxy instance and context.
     *
     * @param proxy The proxy, a generated stub instance or an implementation of a generated interface
     * @return The Python scoped proxy of the intercepted target
     * @throws IllegalStateException When the target is not a Python object
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value interceptedTargetValue(InterceptedProxy<?> proxy) {
        Context context = PythonContextRuntime.getContext();
        return PythonContextRuntime.withExecutionFrame(context, () -> {
            PythonContextRegistry.ContextState state = PythonContextRegistry.state(context);
            synchronized (state.scopedProxies) {
                Value scopedProxy = state.scopedProxies.get(proxy);
                if (scopedProxy != null) {
                    return scopedProxy;
                }
            }
            // the Python class of the generated type the proxy extends or implements; the target itself is
            // not resolved here, a lazy proxy resolves it on the first use
            PythonContextRuntime.PythonClassReference classReference = pythonClassReference(proxy.getClass());
            Value pythonClass = classReference != null
                ? PythonContextRuntime.findClass(classReference, context)
                : interceptedTargetObject(proxy).getMetaObject();
            Value scopedProxy = PythonContextRuntime.helper(context, SCOPED_PROXY_FACTORY)
                .execute(pythonClass, (ProxyExecutable) arguments -> interceptedTargetObject(proxy));
            synchronized (state.scopedProxies) {
                Value existing = state.scopedProxies.putIfAbsent(proxy, scopedProxy);
                return existing == null ? scopedProxy : existing;
            }
        });
    }

    /**
     * Whether a value is an AOP proxy of a generated Python type without a Python object of its own: a
     * proxy implementing a generated interface, which Python code must receive as a Python scoped proxy.
     *
     * @param value The value
     * @return {@code true} for a proxy of a generated Python interface
     */
    static boolean isPythonInterfaceProxy(@Nullable Object value) {
        return value instanceof InterceptedProxy<?> && !(value instanceof ValueCoercible) && pythonClassReference(value.getClass()) != null;
    }

    private static Value interceptedTargetObject(InterceptedProxy<?> proxy) {
        Object target = proxy.interceptedTarget();
        if (target instanceof ValueCoercible valueCoercible) {
            return valueCoercible.asPolyglotValue();
        }
        throw new IllegalStateException("The target of the proxy [" + proxy.getClass().getName() + "] is not a Python object: " + target);
    }

    /**
     * The Python class reference of a generated type in the hierarchy of a class: its superclasses and the
     * interfaces they implement carry the {@link PythonClass} annotation of the generated stub or interface.
     */
    private static PythonContextRuntime.@Nullable PythonClassReference pythonClassReference(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            PythonClass annotation = current.getAnnotation(PythonClass.class);
            if (annotation != null) {
                return pythonClassReference(annotation);
            }
            for (Class<?> anInterface : current.getInterfaces()) {
                PythonContextRuntime.PythonClassReference reference = pythonClassReference(anInterface);
                if (reference != null) {
                    return reference;
                }
            }
        }
        return null;
    }

    private static PythonContextRuntime.PythonClassReference pythonClassReference(PythonClass annotation) {
        return new PythonContextRuntime.PythonClassReference(
            annotation.packageName(),
            annotation.rootName(),
            annotation.nestedMemberNames(),
            annotation.displayName(),
            annotation.cacheKey()
        );
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
     * Assign a member on a Python value, handing a Java collection or a generated wrapper to Python
     * as the host object it is instead of a converted copy.
     *
     * <p>Used by the generated wrapper of an introspected class whose state is owned by its Java
     * fields (the object was created from Java or loaded from storage): the Python attribute then is
     * the Java collection, so an item added or removed in Python is added or removed from the Java
     * field, and a nested object is the Java wrapper, so a write to its attribute reaches the Java
     * field of that wrapper. A frozen dataclass, a polyglot value or a value of a standard type is
     * coerced as by {@link #putMember(Value, String, Object)}.</p>
     *
     * @param target The Python object to update
     * @param name The member name
     * @param value The member value
     */
    @UsedByGeneratedCode
    public static void putMemberByReference(Value target, String name, @Nullable Object value) {
        Context context = target.getContext();
        Object member = switch (value) {
            case null -> null;
            case Collection<?> _, Map<?, ?> _ when !isGuestBackedCollection(value) -> value;
            case PooledValueCoercible _, ValueCoercible _ when !(value instanceof Enum<?>) -> value;
            default -> coerceToContext(value, context);
        };
        memberSetter(context).executeVoid(target, name, member);
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
        return asyncMemberValue(target.getContext(), value);
    }

    static @Nullable Object asyncMemberValue(Context context, @Nullable Object value) {
        if (isInteropPrimitive(value)) {
            return value;
        }
        if (value instanceof CompletionStage<?> completionStage) {
            return PythonAsyncioRuntime.toAwaitable(context, completionStage);
        }
        if (value instanceof PooledValueCoercible || value instanceof Value) {
            return coerceToContext(value, context);
        }
        if (value instanceof ValueCoercible valueCoercible) {
            // a Python bean awaited from an event-loop context must run there
            Value beanValue = PythonContextRuntime.asyncBeanValue(valueCoercible, context);
            if (beanValue != null) {
                return beanValue;
            }
        }
        return asyncMemberFactory(context).execute(value, ASYNC_MEMBER_ADAPTER, context);
    }

    /**
     * Convert a constructor argument of a startup-context object for the replayed constructor in an event-loop
     * context: Python beans and host beans as async members, other values as a constructor call converts them.
     *
     * @param context The event-loop context
     * @param value The Java constructor argument
     * @return The context-local argument
     */
    static @Nullable Object asyncConstructorArgument(Context context, @Nullable Object value) {
        if (isInteropPrimitive(value) || value instanceof PooledValueCoercible || value instanceof Value) {
            return coerceToContext(value, context);
        }
        if (value instanceof ValueCoercible || value instanceof CompletionStage<?>) {
            return asyncMemberValue(context, value);
        }
        Object converted = coerceToContext(value, context);
        return converted == value ? asyncMemberValue(context, value) : converted;
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
        copyTransferableMembers(source, target, Set.of());
    }

    /**
     * Copy simple and host-backed Python instance members into another context, resolving injected Python beans
     * for the target context.
     *
     * @param source The source Python object.
     * @param target The target Python object.
     * @param skippedMembers Members the target owns and that are not copied
     */
    static void copyTransferableMembers(@Nullable Value source, @Nullable Value target, Set<String> skippedMembers) {
        if (source == null || target == null || PythonConversion.isNone(source) || PythonConversion.isNone(target) || !source.hasMembers()) {
            return;
        }
        for (String key : transferableMemberNames(source)) {
            if (key.startsWith("__") || skippedMembers.contains(key)) {
                continue;
            }
            Value member = source.getMember(key);
            Object transferable = transferableMember(member, target.getContext());
            if (transferable != null) {
                putMember(target, key, transferable);
            }
        }
    }

    static List<String> transferableMemberNames(Value source) {
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

    private static @Nullable Object transferableMember(@Nullable Value member, Context targetContext) {
        if (member == null || PythonConversion.isNone(member)) {
            return null;
        }
        if (member.isHostObject()) {
            return member.asHostObject();
        }
        if (member.isProxyObject() && member.asProxyObject() instanceof ValueCoercible valueCoercible) {
            return PythonContextRuntime.asyncBeanValue(valueCoercible, targetContext);
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
     * Complete a future with the first item of a publisher: the publisher is subscribed to, a single
     * item is requested and the subscription is cancelled once it arrives; an empty publisher
     * completes the future with {@code null}.
     *
     * @param publisher The publisher
     * @return The future completed by the publisher
     */
    static PythonAsyncioRuntime.PythonCompletableFuture scalarFuture(Publisher<?> publisher) {
        return scalarFuture(publisher, null);
    }

    /**
     * Complete a future with the first item of a publisher subscribed within a reactive context.
     *
     * @param publisher The publisher
     * @param reactiveContext The reactive context of the subscription, or {@code null} for none
     * @return The future completed by the publisher
     * @see #scalarFuture(Publisher)
     */
    static PythonAsyncioRuntime.PythonCompletableFuture scalarFuture(Publisher<?> publisher, @Nullable PythonReactiveContext reactiveContext) {
        PythonAsyncioRuntime.PythonCompletableFuture future = new PythonAsyncioRuntime.PythonCompletableFuture();
        PythonPublishers.subscribe(publisher, new ScalarPublisherSubscriber(future), reactiveContext);
        return future;
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
            return adaptAwaitable(context, value, null);
        }

        /**
         * Adapt a host async value returned from a Java member to a Python awaitable; a publisher is
         * subscribed within the reactive context of the awaiting coroutine, so the Reactor context
         * of the subscriber that started the coroutine (a reactive transaction status, for instance)
         * reaches it.
         *
         * @param context The target Python context.
         * @param value The host value.
         * @param reactiveContext The reactive context of the coroutine, or {@code null} for none.
         * @return The adapted Python awaitable, or null when the value is not async.
         */
        public @Nullable Value adaptAwaitable(Context context, @Nullable Object value, @Nullable PythonReactiveContext reactiveContext) {
            if (value instanceof CompletionStage<?> completionStage) {
                return PythonAsyncioRuntime.toAwaitable(context, completionStage);
            }
            CompletionStage<?> publisherStage = publisherStage(value, reactiveContext);
            if (publisherStage != null) {
                return PythonAsyncioRuntime.toAwaitable(context, publisherStage);
            }
            if (value instanceof Value polyglotValue) {
                if (polyglotValue.isHostObject()) {
                    Object hostObject = polyglotValue.asHostObject();
                    if (hostObject instanceof CompletionStage<?> completionStage) {
                        return PythonAsyncioRuntime.toAwaitable(context, completionStage);
                    }
                    CompletionStage<?> hostPublisherStage = publisherStage(hostObject, reactiveContext);
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

        private static @Nullable CompletionStage<?> publisherStage(@Nullable Object value, @Nullable PythonReactiveContext reactiveContext) {
            if (!Publishers.isConvertibleToPublisher(value)) {
                return null;
            }
            Publisher<?> publisher;
            try {
                publisher = Publishers.convertToPublisher(ConversionService.SHARED, value);
            } catch (RuntimeException e) {
                return null;
            }
            return scalarFuture(publisher, reactiveContext);
        }
    }
}
