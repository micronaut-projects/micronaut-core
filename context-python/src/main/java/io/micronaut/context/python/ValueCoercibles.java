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
import io.micronaut.core.reflect.ReflectionUtils;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;

/**
 * Recovers the generated {@link ValueCoercible} wrapper behind a polyglot value or proxy object, and
 * matches Python arguments against Java parameter types for the runtime proxies.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
public final class ValueCoercibles {

    private ValueCoercibles() {
    }

    /**
     * Extracts a generated Java wrapper from a polyglot value when one is available.
     * <p>
     * This method recognizes both direct host objects and Micronaut's synthetic
     * {@link ValueCoercible#HOST_OBJECT_MEMBER} back-reference.
     *
     * @param value The polyglot value to inspect.
     * @return The generated wrapper, or {@code null} when the value is not backed by a
     * {@link ValueCoercible}.
     */
    public static @Nullable ValueCoercible hostObject(@Nullable Value value) {
        Object hostObject = rawHostObject(value);
        return hostObject instanceof ValueCoercible valueCoercible ? valueCoercible : null;
    }

    /**
     * Extracts a generated Java wrapper from a proxy object when one is available.
     * <p>
     * This overload is used when code already has a {@link ProxyObject} view and needs to inspect
     * Micronaut's synthetic {@link ValueCoercible#HOST_OBJECT_MEMBER} without first wrapping it as a
     * {@link Value}.
     *
     * @param value The proxy object to inspect.
     * @return The generated wrapper, or {@code null} when the proxy does not expose one.
     */
    public static @Nullable ValueCoercible hostObject(@Nullable ProxyObject value) {
        Object hostObject = rawHostObject(value);
        return hostObject instanceof ValueCoercible valueCoercible ? valueCoercible : null;
    }

    /**
     * Extracts a host object of the requested type from a polyglot value.
     * <p>
     * The returned object may be a direct GraalPy host object or the generated Java wrapper
     * recovered through {@link ValueCoercible#HOST_OBJECT_MEMBER}.
     *
     * @param value The polyglot value to inspect.
     * @param targetType The required host object type.
     * @return The host object when it is assignable to {@code targetType}; otherwise {@code null}.
     */
    public static @Nullable Object hostObject(@Nullable Value value, Class<?> targetType) {
        Object hostObject = rawHostObject(value);
        return targetType.isInstance(hostObject) ? hostObject : null;
    }

    /**
     * Extracts a host object of the requested type from a proxy object.
     * <p>
     * This method checks Micronaut's synthetic {@link ValueCoercible#HOST_OBJECT_MEMBER} and verifies the
     * recovered host object before returning it.
     *
     * @param value The proxy object to inspect.
     * @param targetType The required host object type.
     * @return The host object when it is assignable to {@code targetType}; otherwise {@code null}.
     */
    public static @Nullable Object hostObject(@Nullable ProxyObject value, Class<?> targetType) {
        Object hostObject = rawHostObject(value);
        return targetType.isInstance(hostObject) ? hostObject : null;
    }

    private static @Nullable Object rawHostObject(@Nullable Value value) {
        try {
            if (value == null || value.isNull()) {
                return null;
            }
            if (value.isHostObject()) {
                Object hostObject = value.asHostObject();
                if (hostObject instanceof ValueCoercible.HostObjectReference reference) {
                    return reference.value();
                }
                return hostObject;
            }
            if (!value.hasMembers() || !value.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
                return null;
            }
            Value hostReferenceValue = value.getMember(ValueCoercible.HOST_OBJECT_MEMBER);
            if (hostReferenceValue == null || !hostReferenceValue.isHostObject()) {
                return null;
            }
            Object hostReference = hostReferenceValue.asHostObject();
            return hostReference instanceof ValueCoercible.HostObjectReference reference ? reference.value() : null;
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    private static @Nullable Object rawHostObject(@Nullable ProxyObject value) {
        try {
            if (value == null || !value.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
                return null;
            }
            Object hostReference = value.getMember(ValueCoercible.HOST_OBJECT_MEMBER);
            return hostReference instanceof ValueCoercible.HostObjectReference reference ? reference.value() : null;
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    /**
     * Match one Python argument against a Java parameter type without conversion.
     * <p>
     * Runtime proxies expose all overloads for a Java method name through one Python callable.
     * This quick check uses generated {@code ExecutableMethod} metadata and only boxes primitive
     * types through {@link ReflectionUtils}; it deliberately avoids reflective probing of the
     * generated proxy class while still letting Python-backed values expose their host wrapper via
     * {@link ValueCoercible#HOST_OBJECT_MEMBER}.
     *
     * @param value The Python argument
     * @param targetType The Java parameter type
     * @return Whether the argument can be passed to the generated method
     */
    public static boolean matchesArgument(Value value, Class<?> targetType) {
        Class<?> boxedType = ReflectionUtils.getWrapperType(targetType);
        if (PythonConversion.isNone(value)) {
            return !targetType.isPrimitive();
        }
        if (boxedType == Object.class || boxedType == Value.class) {
            return true;
        }
        Object hostObject = hostObject(value, boxedType);
        if (hostObject != null) {
            return true;
        }
        if (boxedType == String.class) {
            return value.isString();
        }
        if (boxedType == Boolean.class) {
            return value.isBoolean();
        }
        if (boxedType == Byte.class) {
            return value.fitsInByte();
        }
        if (boxedType == Short.class) {
            return value.fitsInShort();
        }
        if (boxedType == Integer.class) {
            return value.fitsInInt();
        }
        if (boxedType == Long.class) {
            return value.fitsInLong();
        }
        if (boxedType == Float.class) {
            return value.fitsInFloat();
        }
        if (boxedType == Double.class) {
            return value.fitsInDouble();
        }
        if (boxedType == Character.class) {
            return value.isString() && value.asString().length() == 1;
        }
        return false;
    }
}
