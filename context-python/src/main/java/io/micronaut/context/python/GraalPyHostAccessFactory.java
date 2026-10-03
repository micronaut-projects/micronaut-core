/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.python.annotation.PythonClass;
import io.micronaut.core.io.service.SoftServiceLoader;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.HostAccess.TargetMappingPrecedence;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Factory that creates the HostAccess bean used by the GraalPy Context.
 * <p>
 * The produced HostAccess is built from HostAccess.ALL and augmented with all
 * available TargetTypeMapping beans discovered by the Micronaut DI container.
 * These mappings enable custom Value.as(Target) conversions for Python→Java types.
 */
@Factory
final class GraalPyHostAccessFactory {

    public static final String CLASS_META = "__class__";
    private static final String FACADE_TARGET = "_target";
    private static final String FACADE_RESOLVED = "_resolved";
    private static final String DECORATOR_CLASS = "java_class";
    private static final String DECORATOR_CLASS_NAME = "java_class_name";

    /** The attributes a Python object is identified by: the resolution order of a class, and the name of any object. */
    private static final String PYTHON_CLASS_MRO = "__mro__";
    private static final String PYTHON_NAME = "__name__";
    private static final String PYTHON_QUALIFIED_NAME = "__qualname__";
    private static final String PYTHON_MODULE = "__module__";
    /** The part a qualified name of a class or function defined inside a function contains. */
    private static final String PYTHON_LOCALS = "<locals>";

    /** The name of the Python datetime module, which its own datetime type shares. */
    private static final String DATETIME = "datetime";
    private static final String BUILTINS = "builtins";
    /**
     * The finite built-in Python containers (besides sequences) accepted by {@code Collection} and
     * {@code Iterable} parameters.
     */
    private static final List<String> FINITE_CONTAINER_TYPES = List.of("set", "frozenset", "dict_keys", "dict_values", "dict_items");

    /** The module of classes defined by the main script, which the compiler places in the top-level package. */
    private static final String MAIN_MODULE = "__main__";

    /**
     * The Python standard library types {@link PythonCoercion} materialises from a Java value, each with
     * the conversion back. Registered both for their own Java type and for an {@code Object} parameter.
     */
    private static final List<StandardLibraryType<?>> STANDARD_LIBRARY_TYPES = List.of(
        new StandardLibraryType<>(DATETIME, "date", LocalDate.class, PythonConversion::convertLocalDate, value -> true),
        new StandardLibraryType<>(DATETIME, "time", LocalTime.class, PythonConversion::convertLocalTime, GraalPyHostAccessFactory::isNaive),
        new StandardLibraryType<>(DATETIME, DATETIME, LocalDateTime.class, PythonConversion::convertLocalDateTime, GraalPyHostAccessFactory::isNaive),
        new StandardLibraryType<>(DATETIME, "timedelta", Duration.class, PythonConversion::convertDuration, value -> true),
        new StandardLibraryType<>(DATETIME, "timezone", ZoneOffset.class, PythonConversion::convertZoneOffset, GraalPyHostAccessFactory::isWholeSecondOffset),
        new StandardLibraryType<>("uuid", "UUID", UUID.class, PythonConversion::convertUuid, value -> true)
    );

    /**
     * The same table keyed by {@code (module, name)}, so a value's type is looked up once rather than
     * compared against every entry in turn.
     */
    private static final Map<String, StandardLibraryType<?>> STANDARD_LIBRARY_TYPES_BY_NAME =
        STANDARD_LIBRARY_TYPES.stream().collect(Collectors.toUnmodifiableMap(
            type -> type.module() + '.' + type.typeName(), type -> type));

    /**
     * Builds a HostAccess instance and registers all TargetTypeMapping beans.
     *
     * @param mappings The discovered TargetTypeMapping beans
     * @param functionalInterfaceProviders The generated providers of the functional interfaces the Python sources reference
     * @param beanContext The bean context, whose class loader loads the generated classes
     * @return A HostAccess configured with custom target type mappings
     */
    @Singleton
    @Named(PythonContextRuntime.PYTHON)
    HostAccess hostAccess(Collection<TargetTypeMapping<?>> mappings,
                          Collection<PythonFunctionalInterfaceProvider> functionalInterfaceProviders,
                          BeanContext beanContext) {
        return hostAccess(mappings, beanContext.getClassLoader(), entries(functionalInterfaceProviders));
    }

    /**
     * Builds a HostAccess instance for a context created outside the bean context, loading the
     * generated classes through the context class loader of the calling thread.
     *
     * @param mappings The TargetTypeMapping instances
     * @return A HostAccess configured with custom target type mappings
     */
    HostAccess hostAccess(Collection<TargetTypeMapping<?>> mappings) {
        return hostAccess(mappings, (ClassLoader) null);
    }

    /**
     * Builds a HostAccess instance and registers all TargetTypeMapping instances.
     *
     * @param mappings The TargetTypeMapping instances
     * @param classLoader The class loader of the generated classes, or {@code null} to use the context
     *                    class loader of the calling thread
     * @return A HostAccess configured with custom target type mappings
     */
    HostAccess hostAccess(Collection<TargetTypeMapping<?>> mappings, @Nullable ClassLoader classLoader) {
        return hostAccess(mappings, classLoader, functionalInterfaces(classLoader));
    }

    /**
     * Builds a HostAccess instance and registers all TargetTypeMapping instances.
     *
     * @param mappings The TargetTypeMapping instances
     * @param classLoader The class loader of the generated classes, or {@code null} to use the context
     *                    class loader of the calling thread
     * @param functionalInterfaces The functional interfaces a callable is converted to by arity,
     *                             besides the standard ones
     * @return A HostAccess configured with custom target type mappings
     */
    HostAccess hostAccess(Collection<TargetTypeMapping<?>> mappings,
                          @Nullable ClassLoader classLoader,
                          Collection<PythonFunctionalInterfaceProvider.Entry> functionalInterfaces) {
        HostAccess.Builder builder = HostAccess.newBuilder(HostAccess.ALL);
        PythonClassResolver pythonClassResolver = new PythonClassResolver(mappings, classLoader);
        Map<Class<?>, List<TargetTypeMapping<?>>> assignableMappings = new LinkedHashMap<>();
        for (TargetTypeMapping<?> mapping : mappings) {
            register(builder, mapping, pythonClassResolver);
            for (Class<?> assignableTargetType : mapping.assignableTargetTypes()) {
                if (assignableTargetType == null || assignableTargetType.equals(mapping.targetType())) {
                    continue;
                }
                assignableMappings
                    .computeIfAbsent(assignableTargetType, ignored -> new ArrayList<>())
                    .add(mapping);
            }
        }
        for (Map.Entry<Class<?>, List<TargetTypeMapping<?>>> entry : assignableMappings.entrySet()) {
            registerAssignable(builder, entry.getKey(), entry.getValue(), pythonClassResolver);
        }
        registerValueCoercibleHostMapping(builder, ValueCoercible.class);
        registerValueCoercibleHostMapping(builder, Throwable.class);
        registerValueCoercibleHostMapping(builder, Exception.class);
        registerValueCoercibleHostMapping(builder, RuntimeException.class);
        registerPythonClassMapping(builder, pythonClassResolver);
        registerObjectMapping(builder, pythonClassResolver);
        registerStandardLibraryMappings(builder);
        registerSequenceMappings(builder);
        PythonCallables.registerFunctionalInterfaces(builder, functionalInterfaces, classLoader);
        registerNumericMappings(builder);
        return builder.build();
    }

    /**
     * The functional interfaces the Python compiler found in the Java types the Python sources
     * reference, from the generated {@link PythonFunctionalInterfaceProvider} services of the class loader.
     */
    private static List<PythonFunctionalInterfaceProvider.Entry> functionalInterfaces(@Nullable ClassLoader classLoader) {
        ClassLoader loader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        return entries(SoftServiceLoader.load(PythonFunctionalInterfaceProvider.class, loader).collectAll());
    }

    private static List<PythonFunctionalInterfaceProvider.Entry> entries(Collection<PythonFunctionalInterfaceProvider> providers) {
        List<PythonFunctionalInterfaceProvider.Entry> entries = new ArrayList<>();
        for (PythonFunctionalInterfaceProvider provider : providers) {
            entries.addAll(provider.entries());
        }
        return entries;
    }

    /**
     * Overload resolution for Python sequences.
     * <p>
     * Host interop only knows {@code List} for array-like guest values, and it accepts any object with
     * members as a {@code Map}. A Python {@code list} passed to overloads such as
     * {@code success(String, Collection)} / {@code success(String, Map)} therefore selected the
     * {@code Map} overload, and {@code Collection} or {@code Iterable} parameters received an
     * interface proxy. These mappings take precedence over the default (loose) conversions so a
     * sequence selects the collection overload, and Python {@code bytes} / {@code bytearray} select
     * a {@code byte[]} overload instead of an {@code Object} or stream one.
     * <p>
     * A sequence is passed as the live host view of the Python object; a set, frozenset or dictionary
     * view is copied into a Java list, so Java-side mutations of that list do not reach Python.
     */
    private static void registerSequenceMappings(HostAccess.Builder builder) {
        builder.targetTypeMapping(Value.class, List.class,
            GraalPyHostAccessFactory::isSequence,
            GraalPyHostAccessFactory::asList);
        builder.targetTypeMapping(Value.class, Collection.class,
            GraalPyHostAccessFactory::isSequenceOrContainer,
            GraalPyHostAccessFactory::asList);
        builder.targetTypeMapping(Value.class, Iterable.class,
            GraalPyHostAccessFactory::isSequenceOrContainer,
            GraalPyHostAccessFactory::asList);
        builder.targetTypeMapping(Value.class, byte[].class,
            GraalPyHostAccessFactory::isBytesLike,
            GraalPyHostAccessFactory::readBytes);
    }

    /**
     * A Python bytes-like value ({@code bytes}, {@code bytearray}, {@code memoryview}, or any object
     * exposing the buffer protocol). A Java {@code byte[]} that went to Python is a host object and
     * keeps the default host conversion, so it comes back as the same array.
     *
     * @param value The value
     * @return {@code true} when the value is a Python buffer
     */
    private static boolean isBytesLike(@Nullable Value value) {
        return value != null && !value.isNull() && !value.isHostObject() && value.hasBufferElements();
    }

    /**
     * A Python sequence (list, tuple): not a host object (a Java byte[] or List keeps the default host
     * conversion, or {@code Files.write(Path, byte[])} would become ambiguous with the Iterable overload)
     * and not a bytes-like buffer, which maps to {@code byte[]}.
     */
    private static boolean isSequence(@Nullable Value value) {
        return value != null && !value.isNull() && !value.isHostObject() && !value.hasBufferElements() && value.hasArrayElements();
    }

    /**
     * A Python sequence or a finite built-in container ({@code set}, {@code frozenset} and the dictionary
     * views): these are copied into a Java list for a {@code Collection} or {@code Iterable} parameter.
     * Other iterables (generators, {@code itertools} iterators) are not matched, because copying would
     * consume a lazy iterable eagerly or never finish for an infinite one; an {@code Iterable} parameter
     * keeps the default lazy host view for them.
     */
    private static boolean isSequenceOrContainer(@Nullable Value value) {
        if (isSequence(value)) {
            return true;
        }
        if (value == null || value.isNull() || value.isHostObject() || !value.hasIterator() || value.hasHashEntries()) {
            return false;
        }
        for (String containerType : FINITE_CONTAINER_TYPES) {
            if (PythonCoercion.isPythonType(value, BUILTINS, containerType)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Value value) {
        if (value.hasArrayElements()) {
            // Value.as(List.class) would re-enter this mapping; the default Object conversion of an
            // array-like value is the same live List view, which keeps the identity of the Python
            // sequence when it is handed back to Python.
            Object converted = value.as(Object.class);
            if (converted instanceof List<?> list) {
                return (List<Object>) list;
            }
            long size = value.getArraySize();
            List<Object> elements = new ArrayList<>(Math.toIntExact(size));
            for (long i = 0; i < size; i++) {
                elements.add(value.getArrayElement(i).as(Object.class));
            }
            return elements;
        }
        List<Object> elements = new ArrayList<>();
        Value iterator = value.getIterator();
        while (iterator.hasIteratorNextElement()) {
            elements.add(iterator.getIteratorNextElement().as(Object.class));
        }
        return elements;
    }

    private static byte[] readBytes(Value value) {
        int size = Math.toIntExact(value.getBufferSize());
        byte[] bytes = new byte[size];
        value.readBuffer(0, bytes, 0, size);
        return bytes;
    }

    /**
     * Numeric parameters. Host interop converts a guest number to a primitive or boxed numeric
     * parameter when the value fits the type losslessly, and selects the most specific of the
     * overloads the arguments fit. These mappings apply the same rule (the predicates are the
     * interop fits-in checks) with the highest precedence, so the selection is unchanged but the
     * cached call site guards its arguments through the mappings instead of the interop primitive
     * type checks. Those checks reject a mix of arguments they were built from, such as
     * {@code of(0.15, 0.25)} for the overloads {@code of(float...)} and {@code of(double...)}
     * (0.25 also fits {@code float}) or {@code of(300, 1)} next to {@code of(byte...)}: with
     * Java assertions enabled (as in a Gradle test JVM) the host call fails with an
     * {@code AssertionError} in {@code HostExecuteNode.fillArgTypesArray}, without them the call
     * site falls back to the uncached path. Parameters of type {@code Number} or
     * {@code BigInteger} are not covered.
     */
    private static void registerNumericMappings(HostAccess.Builder builder) {
        builder.targetTypeMapping(Value.class, Byte.class, GraalPyHostAccessFactory::fitsInByte, Value::asByte, TargetMappingPrecedence.HIGHEST);
        builder.targetTypeMapping(Value.class, Short.class, GraalPyHostAccessFactory::fitsInShort, Value::asShort, TargetMappingPrecedence.HIGHEST);
        builder.targetTypeMapping(Value.class, Integer.class, GraalPyHostAccessFactory::fitsInInt, Value::asInt, TargetMappingPrecedence.HIGHEST);
        builder.targetTypeMapping(Value.class, Long.class, GraalPyHostAccessFactory::fitsInLong, Value::asLong, TargetMappingPrecedence.HIGHEST);
        builder.targetTypeMapping(Value.class, Float.class, GraalPyHostAccessFactory::fitsInFloat, Value::asFloat, TargetMappingPrecedence.HIGHEST);
        builder.targetTypeMapping(Value.class, Double.class, GraalPyHostAccessFactory::fitsInDouble, Value::asDouble, TargetMappingPrecedence.HIGHEST);
    }

    private static boolean fitsInByte(@Nullable Value value) {
        return value != null && value.fitsInByte();
    }

    private static boolean fitsInShort(@Nullable Value value) {
        return value != null && value.fitsInShort();
    }

    private static boolean fitsInInt(@Nullable Value value) {
        return value != null && value.fitsInInt();
    }

    private static boolean fitsInLong(@Nullable Value value) {
        return value != null && value.fitsInLong();
    }

    private static boolean fitsInFloat(@Nullable Value value) {
        return value != null && value.fitsInFloat();
    }

    private static boolean fitsInDouble(@Nullable Value value) {
        return value != null && value.fitsInDouble();
    }

    private static void registerStandardLibraryMappings(HostAccess.Builder builder) {
        for (StandardLibraryType<?> standardType : STANDARD_LIBRARY_TYPES) {
            registerStandardLibraryMapping(builder, standardType);
        }
        registerErasedStandardLibraryMapping(builder);
    }

    private static <T> void registerStandardLibraryMapping(HostAccess.Builder builder, StandardLibraryType<T> standardType) {
        builder.targetTypeMapping(Value.class, standardType.targetType(), standardType::matches, standardType.converter());
    }

    /**
     * The same conversions for a parameter whose type is {@code Object}.
     * <p>
     * {@link PythonCoercion} materialises these six Java types as their Python counterparts on the way
     * out, so a value read back off a Java object is a native Python value: the {@code java.util.UUID}
     * identifier of a Micronaut Data entity is a {@code uuid.UUID} once Python holds it. Handing it
     * straight back only worked while the parameter type named the Java type, because a target type
     * mapping is selected by the declared parameter type. A type variable erases to {@code Object} —
     * {@code CrudRepository.findById(ID)} and {@code existsById(ID)} are the ones that bite — and the
     * unconverted Python object then reached Micronaut Data, which matched no row and raised nothing:
     * {@code findById} answered an empty {@code Optional} and {@code existsById} answered {@code false}
     * for a row that is there.
     * <p>
     * A value the conversion refuses (an aware {@code datetime}, a sub-second {@code timezone} offset)
     * stays the Python object it was: {@code Object} is the catch-all parameter type, so a value that
     * has no Java counterpart must still be passable rather than fail the call.
     */
    private static void registerErasedStandardLibraryMapping(HostAccess.Builder builder) {
        builder.targetTypeMapping(
            Value.class,
            Object.class,
            value -> findStandardLibraryType(value) != null,
            value -> {
                StandardLibraryType<?> standardType = findStandardLibraryType(value);
                // The predicate above is the same lookup, so a value only reaches here having matched.
                return Objects.requireNonNull(standardType).converter().apply(value);
            }
        );
    }

    /**
     * @param value a Python value bound for an {@code Object} parameter
     * @return the standard library type to convert it as, or {@code null} to leave it alone
     */
    private static @Nullable StandardLibraryType<?> findStandardLibraryType(@Nullable Value value) {
        if (value == null || value.isNull() || value.isHostObject()
            || value.isString() || value.isNumber() || value.isBoolean() || !value.hasMembers()) {
            return null;
        }
        Value type = value.getMember(CLASS_META);
        if (type == null || !type.hasMembers()) {
            return null;
        }
        String module = PythonConversion.stringMember(type, PYTHON_MODULE);
        String name = PythonConversion.stringMember(type, PYTHON_NAME);
        if (module == null || name == null) {
            return null;
        }
        StandardLibraryType<?> standardType = STANDARD_LIBRARY_TYPES_BY_NAME.get(module + '.' + name);
        return standardType != null && standardType.convertible().test(value) ? standardType : null;
    }

    /**
     * @param value a Python {@code time} or {@code datetime}
     * @return whether it carries no time zone, and so has a {@code LocalTime} or {@code LocalDateTime}
     * counterpart. An aware one does not, and keeps the default mapping rather than failing the call
     */
    private static boolean isNaive(Value value) {
        Value tzinfo = value.getMember("tzinfo");
        return tzinfo == null || tzinfo.isNull();
    }

    /**
     * @param value a Python {@code datetime.timezone}
     * @return whether its offset is an exact number of seconds, which is all {@link ZoneOffset} can
     * express
     */
    private static boolean isWholeSecondOffset(Value value) {
        try {
            PythonConversion.convertZoneOffset(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Registers a single mapping with the HostAccess builder using Value as the source type.
     * Uses a simple non-null predicate and delegates conversion to the mapping implementation.
     *
     * @param builder The HostAccess builder
     * @param mapping The mapping to register
     * @param <T>     The target type
     */
    private static <T> void register(HostAccess.Builder builder,
                                     TargetTypeMapping<T> mapping,
                                     PythonClassResolver pythonClassResolver) {
        Class<T> target = mapping.targetType();
        builder.targetTypeMapping(
            Value.class,
            target,
            v -> {
                ValueCoercible host = ValueCoercibles.hostObject(v);
                if (host != null && target.isInstance(host)) {
                    return true;
                }
                if (v == null || v.isNull()) {
                    return false;
                }
                if (!v.hasMembers()) {
                    return false;
                }
                Value cls = v.getMember(CLASS_META);
                if (cls == null) {
                    return false;
                }
                return target.equals(pythonClassResolver.findPythonClass(cls));
            },
            v -> {
                ValueCoercible host = ValueCoercibles.hostObject(v);
                if (host != null && target.isInstance(host)) {
                    return target.cast(host);
                }
                return mapping.convert(v);
            }
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerAssignable(HostAccess.Builder builder,
                                           Class<?> target,
                                           List<TargetTypeMapping<?>> targetMappings,
                                           PythonClassResolver pythonClassResolver) {
        builder.targetTypeMapping(
            Value.class,
            (Class) target,
            v -> {
                ValueCoercible host = ValueCoercibles.hostObject(v);
                if (host != null && target.isInstance(host)) {
                    return true;
                }
                return findAssignableMapping(v, targetMappings, pythonClassResolver) != null;
            },
            v -> {
                ValueCoercible host = ValueCoercibles.hostObject(v);
                if (host != null && target.isInstance(host)) {
                    return target.cast(host);
                }
                TargetTypeMapping<?> mapping = findAssignableMapping(v, targetMappings, pythonClassResolver);
                if (mapping == null) {
                    throw new IllegalArgumentException("Cannot resolve Python value to " + target.getName());
                }
                return target.cast(mapping.convert(v));
            }
        );
        registerValueCoercibleAssignableHostMapping(builder, target);
        registerProxyHostMapping(builder, target);
    }

    private static void registerObjectMapping(HostAccess.Builder builder, PythonClassResolver pythonClassResolver) {
        builder.targetTypeMapping(
            Value.class,
            Object.class,
            v -> isBytesLike(v) || ValueCoercibles.hostObject(v) != null || findMapping(v, pythonClassResolver) != null,
            v -> {
                // a generic API used with its type arguments erased, RedisCommands<byte[], byte[]> on a
                // raw-typed bean, takes Object parameters here: a Python bytes value is the byte[] the
                // declared byte[] parameter of the same API would have received
                if (isBytesLike(v)) {
                    return readBytes(v);
                }
                ValueCoercible host = ValueCoercibles.hostObject(v);
                if (host != null) {
                    return host;
                }
                TargetTypeMapping<?> mapping = findMapping(v, pythonClassResolver);
                return mapping == null ? v : mapping.convert(v);
            }
        );
    }

    private static <T> void registerValueCoercibleHostMapping(HostAccess.Builder builder, Class<T> target) {
        builder.targetTypeMapping(
            Value.class,
            target,
            v -> {
                ValueCoercible host = ValueCoercibles.hostObject(v);
                return host != null && target.isInstance(host);
            },
            v -> target.cast(ValueCoercibles.hostObject(v))
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerValueCoercibleAssignableHostMapping(HostAccess.Builder builder, Class<?> target) {
        builder.targetTypeMapping(
            ValueCoercible.class,
            (Class) target,
            target::isInstance,
            target::cast
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerProxyHostMapping(HostAccess.Builder builder, Class<?> target) {
        builder.targetTypeMapping(
            ProxyObject.class,
            (Class) target,
            value -> ValueCoercibles.hostObject(value, target) != null,
            value -> target.cast(ValueCoercibles.hostObject(value, target))
        );
    }

    private static void registerPythonClassMapping(HostAccess.Builder builder, PythonClassResolver pythonClassResolver) {
        builder.targetTypeMapping(
            Value.class,
            Class.class,
            v -> resolvePythonClass(v, pythonClassResolver) != null,
            v -> {
                Class<?> target = resolvePythonClass(v, pythonClassResolver);
                if (target == null) {
                    throw new IllegalArgumentException("Cannot resolve Python class to a generated Java stub");
                }
                return target;
            }
        );
    }

    /**
     * The Java class a Python value stands for when it is passed where a {@code Class} is expected:
     * a generated Python class maps to its Java stub, a generated annotation decorator to the
     * annotation type, and the facade a generated package module binds a Java class to when the
     * class is absent from the class path resolves that class on demand.
     *
     * @param value The value
     * @param pythonClassResolver The resolver of generated Python classes
     * @return The class, or {@code null} if the value does not stand for a Java class
     */
    private static @Nullable Class<?> resolvePythonClass(@Nullable Value value, PythonClassResolver pythonClassResolver) {
        if (value == null || value.isNull()) {
            return null;
        }
        try {
            if (value.hasMembers()) {
                Class<?> javaClass = resolveFacadeClass(value);
                if (javaClass == null) {
                    javaClass = resolveAnnotationDecoratorClass(value);
                }
                if (javaClass == null) {
                    javaClass = resolvePythonAnnotationClass(value, pythonClassResolver);
                }
                if (javaClass != null) {
                    return javaClass;
                }
            }
        } catch (RuntimeException ignored) {
            // Fall through to regular generated Python class resolution.
        }
        try {
            return pythonClassResolver.findPythonClass(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * The class behind the facade a generated package module binds a Java class to when the class
     * is absent from the class path at import time: it holds the class name in {@code _target} and
     * resolves it through {@code _resolved()}.
     */
    private static @Nullable Class<?> resolveFacadeClass(Value value) {
        if (!value.hasMember(FACADE_TARGET)) {
            return null;
        }
        Value target = value.getMember(FACADE_TARGET);
        if (target != null && target.isString()) {
            target = value.hasMember(FACADE_RESOLVED) ? value.invokeMember(FACADE_RESOLVED) : null;
        }
        return target == null || target.isNull() ? null : hostClass(target);
    }

    /**
     * The class a value naming a host class stands for: the {@code java.type(...)} view of the
     * class or a {@code Class} host object.
     */
    private static @Nullable Class<?> hostClass(Value value) {
        if (value.isHostObject() && value.asHostObject() instanceof Class<?> hostClass) {
            return hostClass;
        }
        return value.as(Class.class);
    }

    /**
     * The annotation type a generated annotation decorator stands for. The decorator carries the
     * type as {@code java_class} when the type is loadable from Python, and always its name as
     * {@code java_class_name}, which is loaded through the application class loader otherwise.
     */
    private static @Nullable Class<?> resolveAnnotationDecoratorClass(Value value) {
        if (!value.canExecute() || !value.hasMember(DECORATOR_CLASS_NAME)) {
            return null;
        }
        if (value.hasMember(DECORATOR_CLASS)) {
            Value javaClass = value.getMember(DECORATOR_CLASS);
            if (javaClass != null && !javaClass.isNull()) {
                return hostClass(javaClass);
            }
        }
        Value className = value.getMember(DECORATOR_CLASS_NAME);
        if (className == null || !className.isString()) {
            return null;
        }
        return loadClass(className.asString());
    }

    /**
     * The annotation type a Python-defined annotation stands for. Such an annotation is a Python
     * function (a decorator factory), and the compiler generates its {@code @interface} in the Java
     * package of the module that defines it, under the name of the function, where the resolution of
     * a Python class finds it. Only a generated annotation type is accepted, so an ordinary Python
     * function passed where a {@code Class} is expected is still rejected.
     */
    private static @Nullable Class<?> resolvePythonAnnotationClass(Value value, PythonClassResolver pythonClassResolver) {
        if (!value.canExecute() || value.hasMember(PYTHON_CLASS_MRO)) {
            return null;
        }
        String simpleName = stringMember(value, PYTHON_QUALIFIED_NAME);
        if (simpleName == null || simpleName.isBlank()) {
            simpleName = stringMember(value, PYTHON_NAME);
        }
        if (simpleName == null || simpleName.isBlank() || simpleName.contains(PYTHON_LOCALS)) {
            return null;
        }
        Class<?> resolved = pythonClassResolver.findClass(stringMember(value, PYTHON_MODULE), simpleName);
        return resolved != null && resolved.isAnnotation() ? resolved : null;
    }

    private static @Nullable Class<?> loadClass(String className) {
        return loadClass(className, null);
    }

    /**
     * Loads a class by name through the preferred loader, the loader of the current Python
     * application, the context class loader and this class's loader, in that order.
     *
     * @param className the binary class name
     * @param preferredLoader the loader to try first, if any
     * @return the class, or {@code null} when no loader knows it
     */
    private static @Nullable Class<?> loadClass(String className, @Nullable ClassLoader preferredLoader) {
        PythonApplicationRuntime runtime = PythonApplicationRuntime.current();
        ClassLoader[] loaders = {
            preferredLoader,
            runtime == null ? null : runtime.classLoader(),
            Thread.currentThread().getContextClassLoader(),
            GraalPyHostAccessFactory.class.getClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(className, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // try the next loader
            }
        }
        return null;
    }

    private static @Nullable TargetTypeMapping<?> findMapping(@Nullable Value value, PythonClassResolver pythonClassResolver) {
        if (value == null || value.isNull() || value.isHostObject() || !value.hasMembers()) {
            return null;
        }
        Value cls = value.getMember(CLASS_META);
        if (cls == null) {
            return null;
        }
        Class<?> pythonClass = pythonClassResolver.findPythonClass(cls);
        if (pythonClass == null) {
            return null;
        }
        return pythonClassResolver.mappingForClass(pythonClass);
    }

    private static @Nullable TargetTypeMapping<?> findAssignableMapping(@Nullable Value value,
                                                                       List<TargetTypeMapping<?>> targetMappings,
                                                                       PythonClassResolver pythonClassResolver) {
        if (value == null || value.isNull() || value.isHostObject() || !value.hasMembers()) {
            return null;
        }
        Value cls = value.getMember(CLASS_META);
        if (cls == null) {
            return null;
        }
        Class<?> pythonClass = pythonClassResolver.findPythonClass(cls);
        if (pythonClass == null) {
            return null;
        }
        for (TargetTypeMapping<?> mapping : targetMappings) {
            if (mapping.targetType().equals(pythonClass)) {
                return mapping;
            }
        }
        return null;
    }

    private static @Nullable Class<?> findPythonClass(@Nullable Value value, PythonClassResolver pythonClassResolver) {
        if (value == null || value.isNull() || value.isHostObject() || !value.hasMembers() || !value.hasMember(PYTHON_CLASS_MRO)) {
            return null;
        }
        String className = stringMember(value, PYTHON_NAME);
        String qualifiedName = stringMember(value, PYTHON_QUALIFIED_NAME);
        String moduleName = stringMember(value, PYTHON_MODULE);
        String simpleName = qualifiedName == null || qualifiedName.isBlank() ? className : qualifiedName;
        if (simpleName == null || simpleName.isBlank() || simpleName.contains(PYTHON_LOCALS)) {
            return null;
        }
        return pythonClassResolver.findClass(moduleName, simpleName);
    }

    private static @Nullable String stringMember(Value value, String memberName) {
        if (!value.hasMember(memberName)) {
            return null;
        }
        Value member = value.getMember(memberName);
        if (member == null || member.isNull() || !member.isString()) {
            return null;
        }
        return member.asString();
    }

    /**
     * Resolves a Python class to the Java class the compiler generated for it: through the target
     * type mapping registered for a concrete class, or by loading the generated class by name, which
     * covers the interfaces and abstract classes generated for Python protocols, abstract base classes
     * and introduction types, for which no mapping exists.
     */
    private static final class PythonClassResolver {
        private final Map<Class<?>, TargetTypeMapping<?>> mappingsByTargetType;
        private final Map<String, Class<?>> mappingsByClassName;
        private final Map<String, Optional<Class<?>>> uniqueMappingsBySimpleName;
        private final Map<PythonClassLookupKey, Optional<Class<?>>> resolvedClasses = new ConcurrentHashMap<>();
        private final @Nullable ClassLoader classLoader;

        private PythonClassResolver(Collection<TargetTypeMapping<?>> mappings, @Nullable ClassLoader classLoader) {
            this.classLoader = classLoader;
            Map<Class<?>, TargetTypeMapping<?>> byType = new HashMap<>(mappings.size());
            Map<String, Class<?>> byName = new HashMap<>(mappings.size());
            Map<String, Class<?>> bySimpleName = new HashMap<>(mappings.size());
            Map<String, Boolean> ambiguous = new HashMap<>();
            for (TargetTypeMapping<?> mapping : mappings) {
                Class<?> targetType = mapping.targetType();
                byType.put(targetType, mapping);
                byName.put(targetType.getName(), targetType);
                String simpleName = targetType.getSimpleName();
                Class<?> existing = bySimpleName.putIfAbsent(simpleName, targetType);
                if (existing != null && existing != targetType) {
                    ambiguous.put(simpleName, true);
                }
            }
            Map<String, Optional<Class<?>>> unique = new HashMap<>(bySimpleName.size());
            for (Map.Entry<String, Class<?>> entry : bySimpleName.entrySet()) {
                unique.put(entry.getKey(), ambiguous.containsKey(entry.getKey())
                    ? Optional.empty() : Optional.of(entry.getValue()));
            }
            mappingsByTargetType = Map.copyOf(byType);
            mappingsByClassName = Map.copyOf(byName);
            uniqueMappingsBySimpleName = Map.copyOf(unique);
        }

        private @Nullable TargetTypeMapping<?> mappingForClass(Class<?> pythonClass) {
            return mappingsByTargetType.get(pythonClass);
        }

        private @Nullable Class<?> findPythonClass(@Nullable Value value) {
            return GraalPyHostAccessFactory.findPythonClass(value, this);
        }

        private @Nullable Class<?> findClass(@Nullable String moduleName, String simpleName) {
            String normalized = moduleName == null || moduleName.isBlank() ? null : moduleName;
            return resolvedClasses.computeIfAbsent(new PythonClassLookupKey(normalized, simpleName),
                key -> Optional.ofNullable(resolveClass(key))).orElse(null);
        }

        private @Nullable Class<?> resolveClass(PythonClassLookupKey key) {
            List<String> classNames = generatedClassNames(key.moduleName(), key.simpleName());
            for (String className : classNames) {
                Class<?> exact = mappingsByClassName.get(className);
                if (exact != null) {
                    return exact;
                }
            }
            for (String className : classNames) {
                Class<?> generated = loadGeneratedClass(className);
                if (generated != null) {
                    return generated;
                }
            }
            return uniqueMappingsBySimpleName.getOrDefault(key.simpleName(), Optional.empty()).orElse(null);
        }

        /**
         * The names the Java class generated for a Python class can have, given the module the class is
         * defined in. The compiler places a class in the Java package named after the Python package of
         * its module (the module itself when the class is defined in a package initializer) and puts the
         * classes of a top-level module, and of the main script, in the {@code python} package.
         *
         * @param moduleName The Python module of the class, or {@code null} when unknown
         * @param simpleName The qualified name of the class within its module
         * @return The candidate class names, most specific first
         */
        private static List<String> generatedClassNames(@Nullable String moduleName, String simpleName) {
            String generatedSimpleName = simpleName.replace('.', '$');
            List<String> names = new ArrayList<>(3);
            if (moduleName == null || moduleName.isBlank() || MAIN_MODULE.equals(moduleName)) {
                names.add(PythonContextRuntime.PYTHON + "." + generatedSimpleName);
                return names;
            }
            int packageSeparator = moduleName.lastIndexOf('.');
            if (packageSeparator > 0) {
                names.add(moduleName.substring(0, packageSeparator) + "." + generatedSimpleName);
            } else {
                names.add(PythonContextRuntime.PYTHON + "." + generatedSimpleName);
            }
            String packageInitializerName = moduleName + "." + generatedSimpleName;
            if (!names.contains(packageInitializerName)) {
                names.add(packageInitializerName);
            }
            return names;
        }

        /**
         * Loads a generated class by name. A class is accepted when the Python compiler generated it
         * (it carries {@link PythonClass}) or when it is an interface, the form the compiler gives a
         * Python protocol or abstract base class, so an unrelated Java class that shares the name of
         * a Python class is not mistaken for its generated class.
         */
        private @Nullable Class<?> loadGeneratedClass(String className) {
            Class<?> loaded = loadClass(className, classLoader);
            if (loaded == null) {
                return null;
            }
            return loaded.isInterface() || loaded.isAnnotationPresent(PythonClass.class) ? loaded : null;
        }
    }

    private record PythonClassLookupKey(@Nullable String moduleName, String simpleName) {
    }

    /**
     * A Python standard library type and the Java type it converts to.
     *
     * @param module The Python module declaring the type
     * @param typeName The Python type name
     * @param targetType The Java type
     * @param converter The conversion
     * @param <T> The Java type
     */
    /**
     * A Python standard library type with a Java counterpart, and what it takes to get there.
     *
     * @param module      the Python module the type is defined in
     * @param typeName    the Python type name
     * @param targetType  the Java type it converts to
     * @param converter   the conversion
     * @param convertible whether a given value is one the converter can take. A value it cannot --
     *                    an aware {@code datetime}, an offset finer than a second -- must not match,
     *                    so that it keeps the mapping it would otherwise have had
     * @param <T>         the Java type
     */
    private record StandardLibraryType<T>(String module, String typeName, Class<T> targetType,
                                         Function<Value, T> converter, Predicate<Value> convertible) {

        boolean matches(Value value) {
            return PythonCoercion.isPythonType(value, module, typeName);
        }
    }

}
