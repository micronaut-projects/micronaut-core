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

import io.micronaut.context.python.annotation.PythonClass;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;

/**
 * Conversion of Python values to Java: none checks, scalars, collections, optionals, enums and the
 * generated wrapper types.
 * <p>
 * Maintainer notes. The public methods are called by generated bridge code (see
 * {@code PythonStubGenerator}) on every value that crosses from Python to Java, so they sit on the
 * hot path: keep them free of meta-object lookups and string formatting. The package-private methods
 * are the per-type converters {@link PythonCoercion} and {@link #convertValue} dispatch to. Every
 * converter follows the same rules: {@code None} converts to {@code null} (or an empty
 * {@link Optional}), a value that is already a host object of the target type is returned as is,
 * a generated wrapper ({@link ValueCoercible}) is unwrapped rather than copied, and a conversion
 * that cannot be done is an exception, never an empty result, so a bad element does not silently
 * shrink a collection.
 *
 * @since 5.2.0
 */
@Internal
public final class PythonConversion {

    private static final String UTC_OFFSET = "__micronaut_utc_offset";
    private static final int MAX_META_PARENT_DEPTH = 16;

    private static final String LEN = "__len__";

    private static final String GETITEM = "__getitem__";

    private static final String ISOFORMAT = "isoformat";

    private static final String FROM_POLYGLOT_VALUE = "fromPolyglotValue";

    /** The Java package of the classes of a top-level Python module. */
    private static final String TOP_LEVEL_PACKAGE = "python";

    /**
     * Per declared wrapper type, the generated subclass factory for the Python classes seen as values of
     * that type, keyed by the Python class name; an empty entry records a Python class with no generated
     * subclass wrapper (the declared type itself included).
     */
    private static final ClassValue<ConcurrentHashMap<String, Optional<Method>>> SUBCLASS_FACTORIES = new ClassValue<>() {
        @Override
        protected ConcurrentHashMap<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    /**
     * The Python class a generated wrapper type is generated for, from its {@code PythonClass} annotation:
     * the reference and the key of the class in the per-context class cache. Empty for a type that is not a
     * generated wrapper.
     */
    private static final ClassValue<Optional<OwnPythonClass>> OWN_PYTHON_CLASSES = new ClassValue<>() {
        @Override
        protected Optional<OwnPythonClass> computeValue(Class<?> type) {
            PythonClass annotation = type.getAnnotation(PythonClass.class);
            if (annotation == null) {
                return Optional.empty();
            }
            PythonContextRuntime.PythonClassReference reference = new PythonContextRuntime.PythonClassReference(
                annotation.packageName(),
                annotation.rootName(),
                annotation.nestedMemberNames(),
                annotation.displayName(),
                annotation.cacheKey()
            );
            return Optional.of(new OwnPythonClass(annotation, PythonContextRuntime.classCacheKey(reference)));
        }
    };

    private PythonConversion() {
    }

    /**
     * Returns whether the value represents Java null or Python None.
     *
     * <p>Python {@code None} is an interop null, so {@link Value#isNull()} is the complete check. This
     * method is on the hot path of every bridge call and conversion, so it must not touch the meta
     * object or the string representation of the value.</p>
     *
     * @param value The polyglot value
     * @return Whether the value represents Java null or Python None
     */
    public static boolean isNone(@Nullable Value value) {
        return value == null || value.isNull();
    }

    /**
     * The Java wrapper an earlier crossing created for a Python object, when it is of the type the
     * caller wants.
     * <p>
     * A generated wrapper mirrors the attributes of the Python object in its Java fields, and Java
     * writes to those fields: JPA assigns the generated identifier of an entity to the field of the
     * managed instance. Building a new wrapper on every crossing threw those writes away, so a Python
     * entity persisted through {@code EntityManager.persist(entity)} came back without its id. The
     * wrapper is therefore bound to the Python object (see {@link #bindWrapper(Value, Object)}) and
     * the next crossing reuses it, after refreshing the fields Java has not changed itself.
     *
     * @param value The Python object
     * @param type The wrapper type the caller wants
     * @return The bound wrapper, or {@code null} when the object has none of that type
     * @since 5.2.4
     */
    @UsedByGeneratedCode
    public static @Nullable Object boundWrapper(Value value, Class<?> type) {
        Context context = value.getContext();
        PythonContextRegistry.ContextState state = context == null ? null : PythonContextRegistry.existingState(context);
        if (state == null) {
            return null;
        }
        WeakReference<Object> bound = state.javaWrappers.get(value);
        Object wrapper = bound == null ? null : bound.get();
        return type.isInstance(wrapper) ? wrapper : null;
    }

    /**
     * Bind a Java wrapper to the Python object it wraps, so the next crossing reuses it instead of
     * building a copy Java's writes are lost from.
     * <p>
     * The binding is kept in the state of the context, not as an attribute of the Python object: an
     * attribute would be in the object's {@code __dict__} and {@code copy.deepcopy}, {@code pickle}
     * and {@code dataclasses.asdict} would all try to take the Java wrapper with them.
     *
     * @param value The Python object
     * @param wrapper The wrapper of the object
     * @since 5.2.4
     */
    @UsedByGeneratedCode
    public static void bindWrapper(Value value, Object wrapper) {
        Context context = value.getContext();
        if (context == null) {
            return;
        }
        PythonContextRegistry.state(context).javaWrappers.put(value, new WeakReference<>(wrapper));
    }

    /**
     * Convert a Python {@code datetime.date} through its ISO 8601 form, the one representation both
     * sides parse identically.
     *
     * @param value The Python date
     * @return The local date
     */
    static LocalDate convertLocalDate(Value value) {
        return LocalDate.parse(value.invokeMember(ISOFORMAT).asString());
    }

    /**
     * Convert a naive Python {@code datetime.time}. An aware time (one with {@code tzinfo}) is
     * refused: {@link LocalTime} has no zone and dropping it would change the instant.
     *
     * @param value The Python time
     * @return The local time
     */
    static LocalTime convertLocalTime(Value value) {
        rejectAware(value, "time");
        return LocalTime.parse(value.invokeMember(ISOFORMAT).asString());
    }

    /**
     * Convert a naive Python {@code datetime.datetime}; aware values are refused for the reason given
     * on {@link #convertLocalTime}.
     *
     * @param value The Python datetime
     * @return The local date-time
     */
    static LocalDateTime convertLocalDateTime(Value value) {
        rejectAware(value, "datetime");
        return LocalDateTime.parse(value.invokeMember(ISOFORMAT).asString());
    }

    /**
     * Convert a Python {@code datetime.timedelta} from its normalised {@code days}, {@code seconds}
     * and {@code microseconds} members (Python keeps seconds and microseconds non-negative and
     * pushes the sign into days, so the three add up).
     *
     * @param value The Python timedelta
     * @return The duration
     */
    static Duration convertDuration(Value value) {
        long days = value.getMember("days").asLong();
        long seconds = value.getMember("seconds").asLong();
        long microseconds = value.getMember("microseconds").asLong();
        return Duration.ofDays(days).plusSeconds(seconds).plusNanos(Math.multiplyExact(microseconds, 1_000));
    }

    /**
     * Convert a fixed-offset {@code datetime.timezone} to a {@link ZoneOffset}. Other {@code tzinfo}
     * implementations (zoneinfo regions, whose offset depends on the instant) are refused, and so is
     * an offset with sub-second precision, which {@link ZoneOffset} cannot express. The offset is
     * read by a helper of the runtime module so this class does not depend on {@code tzinfo}
     * internals.
     *
     * @param value The Python timezone
     * @return The zone offset
     */
    static ZoneOffset convertZoneOffset(Value value) {
        if (!PythonCoercion.isPythonType(value, "datetime", "timezone")) {
            throw new IllegalArgumentException("Only fixed-offset datetime.timezone values can be converted to ZoneOffset");
        }
        Value offsetValue = PythonContextRuntime.helper(value.getContext(), UTC_OFFSET).execute(value);
        Duration offset = convertDuration(offsetValue);
        if (offset.getNano() != 0) {
            throw new IllegalArgumentException("datetime.timezone offset must be an exact number of seconds");
        }
        return ZoneOffset.ofTotalSeconds(Math.toIntExact(offset.getSeconds()));
    }

    /**
     * Convert a Python {@code uuid.UUID} through its canonical string form.
     *
     * @param value The Python UUID
     * @return The UUID
     */
    static UUID convertUuid(Value value) {
        return UUID.fromString(value.invokeMember("__str__").asString());
    }

    /**
     * Refuse an aware {@code datetime} value where a naive Java type is expected.
     *
     * @param value The Python time or datetime
     * @param typeName The Python type name for the message
     */
    private static void rejectAware(Value value, String typeName) {
        Value tzinfo = value.getMember("tzinfo");
        if (tzinfo != null && !isNone(tzinfo)) {
            throw new IllegalArgumentException("Aware datetime." + typeName + " values cannot be converted to a naive Java type");
        }
    }

    /**
     * Read a string member, treating a missing member and {@code None} alike.
     *
     * @param value The Python object
     * @param name The member name
     * @return The member as a string, or {@code null}
     */
    static @Nullable String stringMember(Value value, String name) {
        Value member = value.getMember(name);
        return member == null || member.isNull() ? null : member.asString();
    }

    /**
     * The wrapper of a Python object for the generated subclass it is an instance of, when the object's
     * own class is a generated subclass of the declared wrapper type: a Python subclass instance passed
     * or returned as its Python base type then keeps its runtime type, and with it its introspection.
     * Returns {@code null} when the object is of the declared type itself, of a class with no generated
     * wrapper, or not a Python object at all, in which case the caller wraps it as the declared type.
     *
     * <p>The lookup reads the module and qualified name of the object's class; the resolved factory
     * is cached per declared type and Python class.</p>
     *
     * @param value        The Python object
     * @param declaredType The declared wrapper type
     * @param <T>          The declared wrapper type
     * @return The wrapper of the object's own generated class, or {@code null}
     */
    @UsedByGeneratedCode
    public static <T> @Nullable T subclassWrapper(@Nullable Value value, Class<T> declaredType) {
        if (value == null || value.isNull() || value.isHostObject() || value.isProxyObject() || !value.hasMembers()) {
            return null;
        }
        Method factory;
        try {
            Value pythonClass = value.getMetaObject();
            if (pythonClass == null || !pythonClass.hasMembers() || isOwnPythonClass(pythonClass, declaredType, value.getContext())) {
                return null;
            }
            String qualifiedName = stringMember(pythonClass, "__qualname__");
            if (qualifiedName == null || qualifiedName.contains("<locals>")) {
                return null;
            }
            String moduleName = stringMember(pythonClass, "__module__");
            String key = moduleName == null ? qualifiedName : moduleName + '.' + qualifiedName;
            factory = SUBCLASS_FACTORIES.get(declaredType)
                .computeIfAbsent(key, ignored -> Optional.ofNullable(findSubclassFactory(declaredType, moduleName, qualifiedName)))
                .orElse(null);
        } catch (RuntimeException e) {
            // a value whose class cannot be inspected is wrapped as the declared type
            return null;
        }
        if (factory == null) {
            return null;
        }
        try {
            return declaredType.cast(factory.invoke(null, value));
        } catch (IllegalAccessException e) {
            throw wrapFailure(factory, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw wrapFailure(factory, cause);
        }
    }

    private static IllegalStateException wrapFailure(Method factory, Throwable cause) {
        String message = "Cannot wrap Python value as [%s]: %s".formatted(factory.getDeclaringClass().getName(), cause.getMessage());
        return new IllegalStateException(message, cause);
    }

    /**
     * Whether a Python class is the one the declared wrapper type is generated for: the common case of an
     * object of exactly the declared type, answered without reading the class's name members. The class
     * is looked up in the per-context class cache only; a class the runtime never resolved in the context
     * is compared by name instead.
     */
    private static boolean isOwnPythonClass(Value pythonClass, Class<?> declaredType, Context context) {
        Optional<OwnPythonClass> own = OWN_PYTHON_CLASSES.get(declaredType);
        if (own.isEmpty()) {
            return false;
        }
        Value ownClass = PythonContextRegistry.state(context).classes.get(own.get().cacheKey());
        return ownClass != null && ownClass.equals(pythonClass);
    }

    /**
     * The {@code fromPolyglotValue} factory of the generated wrapper of a Python class, when that wrapper is
     * a proper subtype of the declared type. A Python class {@code C} of the module {@code a.b.m} is
     * generated as {@code a.b.C} (a nested class {@code O.I} as {@code a.b.O$I}); a class of a source-root
     * module has the default package, and a subclass is also looked up next to the declared type. A
     * candidate is accepted only when its {@code PythonClass} annotation names the object's class: the
     * root name of the qualified name, in the package of the module (or the module itself, for a class of
     * a package initializer; {@code python} for a top-level module), so a same-named class of another
     * module is not mistaken for it.
     */
    private static @Nullable Method findSubclassFactory(Class<?> declaredType, @Nullable String moduleName, String qualifiedName) {
        String simpleName = qualifiedName.replace('.', '$');
        Set<String> candidates = new LinkedHashSet<>();
        if (moduleName != null && !moduleName.isBlank()) {
            int lastDot = moduleName.lastIndexOf('.');
            if (lastDot > 0) {
                candidates.add(moduleName.substring(0, lastDot) + '.' + simpleName);
            }
            candidates.add(moduleName + '.' + simpleName);
        }
        candidates.add(declaredType.getPackageName() + '.' + simpleName);
        candidates.add(TOP_LEVEL_PACKAGE + '.' + simpleName);
        ClassLoader classLoader = declaredType.getClassLoader();
        for (String candidate : candidates) {
            Class<?> type = null;
            try {
                type = Class.forName(candidate, false, classLoader);
            } catch (ClassNotFoundException | LinkageError e) {
            }
            if (type != null && type != declaredType && declaredType.isAssignableFrom(type) && isGeneratedFor(type, moduleName, qualifiedName)) {
                try {
                    Method factory = type.getMethod(FROM_POLYGLOT_VALUE, Value.class);
                    // the factory must be the subclass's own: an inherited one is the declared type's, which
                    // would wrap the value as the declared type again
                    if (Modifier.isStatic(factory.getModifiers()) && factory.getDeclaringClass() == type) {
                        return factory;
                    }
                } catch (NoSuchMethodException e) {
                    // not a generated wrapper
                }
            }
        }
        return null;
    }

    /**
     * Whether a generated wrapper type is the one of the Python class with the given module and qualified
     * name, by its {@code PythonClass} annotation.
     */
    private static boolean isGeneratedFor(Class<?> type, @Nullable String moduleName, String qualifiedName) {
        Optional<OwnPythonClass> own = OWN_PYTHON_CLASSES.get(type);
        if (own.isEmpty()) {
            return false;
        }
        PythonClass annotation = own.get().annotation();
        int firstDot = qualifiedName.indexOf('.');
        String rootName = firstDot > 0 ? qualifiedName.substring(0, firstDot) : qualifiedName;
        if (!annotation.rootName().equals(rootName)) {
            return false;
        }
        String packageName = annotation.packageName();
        if (moduleName == null || moduleName.isBlank()) {
            return TOP_LEVEL_PACKAGE.equals(packageName);
        }
        int lastDot = moduleName.lastIndexOf('.');
        if (lastDot < 0) {
            return TOP_LEVEL_PACKAGE.equals(packageName) || moduleName.equals(packageName);
        }
        return moduleName.equals(packageName) || moduleName.substring(0, lastDot).equals(packageName);
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
        return ValueCoercibles.hostObject(value, targetType);
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
        if (graalValue.isHostObject() && graalValue.asHostObject() instanceof List<?> list) {
            // a Java list that went through Python comes back as the same list, not a copy
            @SuppressWarnings("unchecked") List<T> host = (List<T>) list;
            return host;
        }
        // A failing element conversion is an error, not an empty result.
        return convertElements(graalValue, element -> convertValue(element, elementType));
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
        return convertElements(graalValue, converter::convert);
    }

    /**
     * Convert the elements of a Python container in one pass: sequences through the array protocol,
     * iterables (sets, dict views, generators) through the interop iterator, and objects that only
     * implement the sequence protocol through {@code __len__} and {@code __getitem__}.
     */
    private static <T> List<T> convertElements(Value container, Function<Value, T> converter) {
        if (container.hasArrayElements()) {
            long size = container.getArraySize();
            List<T> result = new ArrayList<>(Math.toIntExact(size));
            for (long i = 0; i < size; i++) {
                result.add(converter.apply(container.getArrayElement(i)));
            }
            return result;
        }
        if (container.hasIterator()) {
            List<T> result = new ArrayList<>();
            Value iterator = container.getIterator();
            while (iterator.hasIteratorNextElement()) {
                result.add(converter.apply(iterator.getIteratorNextElement()));
            }
            return result;
        }
        if (container.canInvokeMember(LEN) && container.canInvokeMember(GETITEM)) {
            long size = container.invokeMember(LEN).asLong();
            List<T> result = new ArrayList<>(Math.toIntExact(size));
            for (long i = 0; i < size; i++) {
                result.add(converter.apply(container.invokeMember(GETITEM, i)));
            }
            return result;
        }
        return List.of();
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
        if (graalValue.isHostObject() && graalValue.asHostObject() instanceof Map<?, ?> map) {
            // a Java map that went through Python comes back as the same map, not a copy
            @SuppressWarnings("unchecked") Map<K, V> host = (Map<K, V>) map;
            return host;
        }
        Map<K, V> result = new HashMap<>();
        if (graalValue.hasHashEntries()) {
            Value entries = graalValue.getHashEntriesIterator();
            while (entries.hasIteratorNextElement()) {
                Value entry = entries.getIteratorNextElement();
                result.put(convertValue(entry.getArrayElement(0), keyType), convertValue(entry.getArrayElement(1), valueType));
            }
            return result;
        }
        if (!graalValue.canInvokeMember("keys")) {
            throw new IllegalArgumentException("Cannot convert Python value to a Map: " + graalValue);
        }
        Value keysValue = graalValue.invokeMember("keys");
        if (keysValue != null && keysValue.hasIterator()) {
            Value iterator = keysValue.getIterator();
            while (iterator.hasIteratorNextElement()) {
                Value nextValue = iterator.getIteratorNextElement();
                // A failing entry conversion is an error, not a dropped entry.
                result.put(convertValue(nextValue, keyType), convertValue(graalValue.invokeMember(GETITEM, nextValue), valueType));
            }
        }
        return result;
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

        if (graalValue.isHostObject() && graalValue.asHostObject() instanceof Set<?> hostSet) {
            // a Java set that went through Python comes back as the same set, not a copy
            @SuppressWarnings("unchecked") Set<T> host = (Set<T>) hostSet;
            return host;
        }
        Set<@Nullable T> result = new HashSet<>();
        if (!graalValue.hasIterator()) {
            throw new IllegalArgumentException("Cannot convert Python value to a Set: " + graalValue);
        }
        Value iterator = graalValue.getIterator();
        while (iterator.hasIteratorNextElement()) {
            // A failing element conversion is an error, not a dropped element.
            result.add(convertValue(iterator.getIteratorNextElement(), elementType));
        }
        return result;
    }

    /**
     * Generic value conversion method that handles primitives and recursively converts collections.
     * <p>
     * The attempts run in this order, and the order matters: a host object that is a
     * {@link ValueCoercible} proxy is unwrapped first (a generated wrapper that went through Python
     * must come back as the same Java object, not a copy); a host object of the target type is
     * returned as is; a Java enum target is resolved from the Python enum's name or value; a mapped
     * wrapper type ({@link TargetTypeMapping}) is resolved through the wrapper registry; and only
     * then does {@link Value#as(Class)} run, which handles primitives, strings and the collection
     * types by GraalPy's own rules.
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
        Object container = convertNestedContainer(value, targetType);
        if (container != null) {
            return (T) container;
        }
        return value.as(targetType);
    }

    /**
     * Convert a Python container whose element types are unknown (the element of a {@code list[list[int]]}
     * attribute reaches this method as a plain {@link List}) to a Java collection with value semantics, so two
     * conversions of equal Python data are equal. {@link Value#as(Class)} would return a view of the Python object
     * that compares by identity, which breaks the generated {@code equals}/{@code hashCode} of the classes holding
     * such attributes. Nested containers are converted the same way; every other element is mapped by GraalPy.
     *
     * @param value The value
     * @param targetType The target type
     * @return The Java collection, or {@code null} when the target is not a plain collection type
     */
    private static @Nullable Object convertNestedContainer(Value value, Class<?> targetType) {
        if (value.isHostObject() || value.isString() || value.isNull()) {
            return null;
        }
        if ((targetType == List.class || targetType == Collection.class || targetType == Iterable.class) && value.hasArrayElements()) {
            return convertElements(value, PythonConversion::convertNestedElement);
        }
        if (targetType == Set.class && value.hasIterator() && !value.hasArrayElements()) {
            Set<@Nullable Object> result = new HashSet<>();
            Value iterator = value.getIterator();
            while (iterator.hasIteratorNextElement()) {
                result.add(convertNestedElement(iterator.getIteratorNextElement()));
            }
            return result;
        }
        if (targetType == Map.class && value.hasHashEntries()) {
            Map<@Nullable Object, @Nullable Object> result = new HashMap<>();
            Value entries = value.getHashEntriesIterator();
            while (entries.hasIteratorNextElement()) {
                Value entry = entries.getIteratorNextElement();
                result.put(convertNestedElement(entry.getArrayElement(0)), convertNestedElement(entry.getArrayElement(1)));
            }
            return result;
        }
        return null;
    }

    private static @Nullable Object convertNestedElement(Value element) {
        if (!element.isHostObject() && !element.isString() && !element.isNull()) {
            if (element.hasArrayElements()) {
                return convertNestedContainer(element, List.class);
            }
            if (element.hasHashEntries()) {
                return convertNestedContainer(element, Map.class);
            }
            if (isPythonSet(element)) {
                return convertNestedContainer(element, Set.class);
            }
        }
        return convertValue(element, Object.class);
    }

    private static boolean isPythonSet(Value value) {
        if (!value.hasIterator()) {
            return false;
        }
        Value metaObject = value.getMetaObject();
        return metaObject != null && isPythonSetType(metaObject, 0);
    }

    /**
     * Whether a Python type is {@code set} or {@code frozenset} or derives from one of them: the parents of
     * the type are walked, so a {@code set} subclass converts like a set.
     */
    private static boolean isPythonSetType(Value metaObject, int depth) {
        String typeName = metaObject.getMetaSimpleName();
        if ("set".equals(typeName) || "frozenset".equals(typeName)) {
            return true;
        }
        if (depth > MAX_META_PARENT_DEPTH || !metaObject.hasMetaParents()) {
            return false;
        }
        Value parents = metaObject.getMetaParents();
        for (long i = 0; i < parents.getArraySize(); i++) {
            if (isPythonSetType(parents.getArrayElement(i), depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Convert a value returned to the HTTP layer (a response body, a publisher element) to a Java
     * object while keeping generated wrappers intact: a wrapper's host object is returned rather than
     * a Python view of it, so the response is serialised by its Java type. A plain Python value is
     * converted with {@link Value#as(Class) Value.as(Object.class)}, whatever GraalPy maps it to.
     *
     * @param rawBody The raw body: a polyglot value, a proxy, or already a Java object
     * @return The Java object, or {@code null} for a null body
     */
    static @Nullable Object convertObjectResponseBody(@Nullable Object rawBody) {
        if (rawBody == null) {
            return null;
        }
        if (rawBody instanceof ProxyObject proxyObject && proxyObject.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            ValueCoercible host = ValueCoercibles.hostObject(proxyObject);
            if (host != null) {
                return host;
            }
        }
        if (rawBody instanceof Value value) {
            if (value.isHostObject()) {
                return convertObjectResponseBody(value.asHostObject());
            }
            Object host = ValueCoercibles.hostObject(value, Object.class);
            if (host != null) {
                return host;
            }
            return value.as(Object.class);
        }
        return rawBody;
    }

    /**
     * Resolve a value to a generated wrapper of the target type when the value is, or carries, one:
     * the host object behind a proxy, a host object that is a wrapper proxy, or the object GraalPy
     * maps the value to. Returns {@code null} when none applies so {@link #convertValue} can fall
     * back to {@link Value#as(Class)}; the interop exceptions caught are the ones GraalPy raises
     * when the value is not mappable to a Java object at all.
     *
     * @param value The value
     * @param targetType The wrapper type
     * @param <T> The wrapper type
     * @return The wrapper, or {@code null}
     */
    private static <T> @Nullable T convertMappedWrapper(Value value, Class<T> targetType) {
        try {
            // a generated wrapper, or the AOP proxy a Python scoped proxy stands in for
            Object host = ValueCoercibles.hostObject(value, targetType);
            if (host != null) {
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

    /**
     * Resolve a proxy that fronts a generated wrapper: the wrapper itself when it is of the target
     * type, otherwise a conversion of the wrapper's Python value (a wrapper of a subtype, say, being
     * converted to a supertype's wrapper).
     *
     * @param proxyObject The proxy
     * @param targetType The target type
     * @param <T> The target type
     * @return The converted value, or {@code null} when the proxy fronts no wrapper
     */
    static <T> @Nullable T convertValueCoercibleProxy(ProxyObject proxyObject, Class<T> targetType) {
        ValueCoercible host = ValueCoercibles.hostObject(proxyObject);
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

    /**
     * Resolve a Java enum constant from a Python enum member, a string, or anything whose string
     * form ends in the member name. The constant name is tried first; a constant whose
     * {@code toString()} matches is the fallback for enums that override it.
     *
     * @param value The Python value
     * @param targetType The target type
     * @param <T> The target type
     * @return The constant, or {@code null} when the target is not an enum or nothing matches
     */
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

    /**
     * The name to look a Java enum constant up by: the string itself, a Python enum member's
     * {@code name}, then its {@code value}, then the last segment of {@code str(value)}
     * ({@code Color.RED} gives {@code RED}).
     *
     * @param value The Python value
     * @return The candidate name, or {@code null}
     */
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

    /**
     * A member of a Python enum member as a string, or {@code null} when absent or {@code None}.
     *
     * @param value The Python enum member
     * @param memberName {@code name} or {@code value}
     * @return The member as a string, or {@code null}
     */
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
     * The Python class a generated wrapper type is generated for.
     *
     * @param annotation The annotation of the wrapper type
     * @param cacheKey The key of the class in the per-context class cache
     */
    private record OwnPythonClass(PythonClass annotation, String cacheKey) {
    }
}
