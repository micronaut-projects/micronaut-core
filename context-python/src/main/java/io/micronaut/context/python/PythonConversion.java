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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
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

    private static final String LEN = "__len__";

    private static final String GETITEM = "__getitem__";

    private static final String ISOFORMAT = "isoformat";

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

        Set<@Nullable T> result = new HashSet<>();
        if (graalValue.isHostObject() && graalValue.asHostObject() instanceof Set<?> hostSet) {
            for (Object element : hostSet) {
                @SuppressWarnings("unchecked") T cast = (T) element;
                result.add(cast);
            }
            return result;
        }
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
        return value.as(targetType);
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
            ValueCoercible host = ValueCoercibles.hostObject(value);
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
            ValueCoercible host = ValueCoercibles.hostObject(value);
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
}
