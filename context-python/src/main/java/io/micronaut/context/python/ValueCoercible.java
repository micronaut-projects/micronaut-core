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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.graal.Boxed;
import io.micronaut.core.reflect.ReflectionUtils;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;

import java.beans.Transient;

/**
 * Generated Java wrapper for a Python object that can expose its underlying polyglot value.
 * <p>
 * Micronaut Python bridge classes implement this interface so Java code can keep a strongly typed
 * wrapper while GraalPy can still interact with the original Python object through
 * {@link ProxyObject}. The interface also defines Micronaut-specific proxy members used to recover
 * the generated host wrapper and to bridge generated JavaBean-style accessors back to Python
 * attributes.
 */
@SuppressWarnings({"checkstyle:InnerTypeLast", "checkstyle:MissingJavadocType"})
@Experimental
public interface ValueCoercible extends Boxed<Value>, ProxyObject {
    String HOST_OBJECT_MEMBER = "__micronaut_value_coercible_host__";
    String AS_POLYGLOT_VALUE_MEMBER = "asPolyglotValue";

    /**
     * Returns the wrapped Python value.
     * <p>
     * The returned value belongs to the runtime context that created this wrapper, except for
     * pooled wrappers where {@link PooledValueCoercible#asPolyglotValue(org.graalvm.polyglot.Context)}
     * can resolve an equivalent value for a specific event-loop context.
     *
     * @return The wrapped Python polyglot value.
     */
    @NonNull Value asPolyglotValue();

    /**
     * Unboxes this wrapper for Micronaut conversion infrastructure.
     *
     * @return The wrapped Python polyglot value.
     */
    @Override
    default Value $unbox() {
        return asPolyglotValue();
    }

    /**
     * Exposes the wrapped Python value as a polyglot proxy member.
     * <p>
     * Generated Python bridge classes implement {@link ProxyObject} through this interface so
     * GraalPy can read members from the underlying Python object. Two Micronaut-specific members
     * are handled before delegating to Python:
     * {@link #HOST_OBJECT_MEMBER} exposes a private host reference used to recover the generated
     * Java wrapper, and {@link #AS_POLYGLOT_VALUE_MEMBER} exposes a zero-argument callable that
     * returns the wrapped {@link Value}. JavaBean-style generated accessor aliases are resolved
     * after direct Python members.
     *
     * @param key The requested member name.
     * @return The member value, a generated accessor callable, or {@code null} when the Python
     * member is {@code None} or no member is available.
     */
    @Override
    default @Nullable Object getMember(String key) {
        if (HOST_OBJECT_MEMBER.equals(key)) {
            return new HostObjectReference(this);
        }
        if (AS_POLYGLOT_VALUE_MEMBER.equals(key)) {
            return (ProxyExecutable) arguments -> {
                if (arguments.length != 0) {
                    throw new IllegalArgumentException("asPolyglotValue expects no arguments");
                }
                return asPolyglotValue();
            };
        }
        Value value = asPolyglotValue();
        if (value.hasMember(key)) {
            Value member = value.getMember(key);
            if (GraalPyRuntimeUtil.isNone(member)) {
                return null;
            }
            return member;
        }
        Object getter = generatedGetter(key);
        if (getter != null) {
            return getter;
        }
        return generatedSetter(key);
    }

    /**
     * Returns member keys exposed by the wrapped Python object.
     * <p>
     * Micronaut-specific synthetic members are intentionally omitted from the returned key set so
     * Python-side enumeration reflects the user object's members.
     *
     * @return The wrapped Python member names as a {@code String[]}, or an empty array when the
     * wrapped value has no members.
     */
    @Override
    @Transient
    default Object getMemberKeys() {
        Value value = asPolyglotValue();
        if (value.hasMembers()) {
            return value.getMemberKeys().toArray(new String[0]);
        }
        return new String[0];
    }

    /**
     * Tests whether a member is available through this proxy.
     * <p>
     * The check includes Micronaut's synthetic bridge members, direct members of the wrapped
     * Python object, and generated JavaBean accessor aliases for bridge classes that implement
     * {@link GeneratedPropertyMembers}.
     *
     * @param key The member name to test.
     * @return {@code true} when the member can be resolved.
     */
    @Override
    default boolean hasMember(String key) {
        return HOST_OBJECT_MEMBER.equals(key) ||
            AS_POLYGLOT_VALUE_MEMBER.equals(key) ||
            asPolyglotValue().hasMember(key) ||
            (this instanceof GeneratedPropertyMembers generatedMembers &&
                (generatedMembers.micronautValueCoercibleGetterPropertyName(key) != null ||
                    generatedMembers.micronautValueCoercibleSetterPropertyName(key) != null));
    }

    /**
     * Build-time generated JavaBean accessor aliases for Python wrappers.
     * <p>
     * Only generated classes that need property aliases implement this contract. That keeps
     * the generic {@link ValueCoercible} path free of Java method/field reflection while still
     * preserving Python-side calls such as {@code getName()} for generated bean wrappers.
     */
    interface GeneratedPropertyMembers {
        /**
         * Resolves a generated JavaBean getter method name to the Python property it should read.
         * <p>
         * Generated wrappers use this for aliases such as {@code getName()} when the underlying
         * Python object only has a {@code name} attribute.
         *
         * @param key The requested proxy member name.
         * @return The Python property name, or {@code null} when the member is not a generated
         * getter alias.
         */
        @Transient
        @Nullable String micronautValueCoercibleGetterPropertyName(String key);

        /**
         * Resolves a generated JavaBean setter method name to the Python property it should write.
         * <p>
         * Generated wrappers use this for aliases such as {@code setName(value)} when the
         * underlying Python object only has a {@code name} attribute.
         *
         * @param key The requested proxy member name.
         * @return The Python property name, or {@code null} when the member is not a generated
         * setter alias.
         */
        @Transient
        @Nullable String micronautValueCoercibleSetterPropertyName(String key);

        /**
         * Gives generated wrappers a chance to handle an incoming member assignment directly.
         * <p>
         * Returning {@code true} means the assignment has been fully handled and the generic
         * {@link ValueCoercible#putMember(String, Value)} path must not write the member again.
         *
         * @param key The member being assigned.
         * @param value The incoming polyglot value.
         * @return {@code true} when the generated wrapper consumed the assignment.
         */
        @Transient
        default boolean micronautValueCoercibleSetMember(String key, Value value) {
            return false;
        }

        /**
         * Receives the value that was written to the wrapped Python object.
         * <p>
         * Generated wrappers use this hook to keep Java-side fields and remembered async members in
         * sync with Python-side property writes.
         *
         * @param key The Python property or member name that was written.
         * @param value The value now stored on the wrapped Python object.
         * @return {@code true} when the generated wrapper consumed the notification.
         */
        @Transient
        default boolean micronautValueCoerciblePutMember(String key, Value value) {
            return false;
        }
    }

    private @Nullable Object generatedGetter(String key) {
        if (!(this instanceof GeneratedPropertyMembers generatedMembers)) {
            return null;
        }
        String propertyName = generatedMembers.micronautValueCoercibleGetterPropertyName(key);
        if (propertyName == null) {
            return null;
        }
        return (ProxyExecutable) arguments -> {
            if (arguments.length != 0) {
                throw new IllegalArgumentException("Getter [" + key + "] expects no arguments");
            }
            Value member = asPolyglotValue().getMember(propertyName);
            if (GraalPyRuntimeUtil.isNone(member)) {
                return null;
            }
            return member;
        };
    }

    private @Nullable Object generatedSetter(String key) {
        if (!(this instanceof GeneratedPropertyMembers generatedMembers)) {
            return null;
        }
        String propertyName = generatedMembers.micronautValueCoercibleSetterPropertyName(key);
        if (propertyName == null) {
            return null;
        }
        return (ProxyExecutable) arguments -> {
            if (arguments.length != 1) {
                throw new IllegalArgumentException("Setter [" + key + "] expects one argument");
            }
            Value target = asPolyglotValue();
            Value value = arguments[0];
            if (!generatedMembers.micronautValueCoercibleSetMember(key, value)) {
                if (GraalPyRuntimeUtil.isNone(value)) {
                    target.putMember(propertyName, null);
                } else {
                    target.putMember(propertyName, value);
                }
            }
            generatedMembers.micronautValueCoerciblePutMember(propertyName, target.getMember(propertyName));
            return null;
        };
    }

    /**
     * Writes a member to the wrapped Python object.
     * <p>
     * Python {@code None} is converted to Java {@code null}. Host objects that are themselves
     * {@link ValueCoercible} instances are unwrapped to their Python value before assignment, and
     * {@link PooledValueCoercible} instances are resolved in the target Python context so pooled
     * event-loop objects are not mixed between contexts.
     *
     * @param key The Python member name to write.
     * @param value The incoming polyglot value.
     */
    @Override
    default void putMember(String key, Value value) {
        if (this instanceof GeneratedPropertyMembers generatedMembers
            && generatedMembers.micronautValueCoercibleSetMember(key, value)) {
            return;
        }
        Value target = asPolyglotValue();
        Object member = value;
        if (GraalPyRuntimeUtil.isNone(value)) {
            member = null;
        } else if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            if (hostObject instanceof PooledValueCoercible pooledValueCoercible) {
                member = pooledValueCoercible.asPolyglotValue(target.getContext());
            } else if (hostObject instanceof ValueCoercible valueCoercible) {
                member = valueCoercible.asPolyglotValue();
            }
        }
        target.putMember(key, member);
        if (this instanceof GeneratedPropertyMembers generatedMembers && target.hasMember(key)) {
            generatedMembers.micronautValueCoerciblePutMember(key, target.getMember(key));
        }
    }

    /**
     * Private host reference exposed through {@link #HOST_OBJECT_MEMBER}.
     * <p>
     * GraalPy can box proxy members as host objects, and this record gives Micronaut a stable
     * marker that distinguishes an intentional bridge back-reference from ordinary user members.
     *
     * @param value The generated Java wrapper behind the Python proxy.
     */
    record HostObjectReference(ValueCoercible value) {
    }

    /**
     * Extracts a generated Java wrapper from a polyglot value when one is available.
     * <p>
     * This method recognizes both direct host objects and Micronaut's synthetic
     * {@link #HOST_OBJECT_MEMBER} back-reference.
     *
     * @param value The polyglot value to inspect.
     * @return The generated wrapper, or {@code null} when the value is not backed by a
     * {@link ValueCoercible}.
     */
    static @Nullable ValueCoercible hostObject(@Nullable Value value) {
        Object hostObject = rawHostObject(value);
        return hostObject instanceof ValueCoercible valueCoercible ? valueCoercible : null;
    }

    /**
     * Extracts a generated Java wrapper from a proxy object when one is available.
     * <p>
     * This overload is used when code already has a {@link ProxyObject} view and needs to inspect
     * Micronaut's synthetic {@link #HOST_OBJECT_MEMBER} without first wrapping it as a
     * {@link Value}.
     *
     * @param value The proxy object to inspect.
     * @return The generated wrapper, or {@code null} when the proxy does not expose one.
     */
    static @Nullable ValueCoercible hostObject(@Nullable ProxyObject value) {
        Object hostObject = rawHostObject(value);
        return hostObject instanceof ValueCoercible valueCoercible ? valueCoercible : null;
    }

    /**
     * Extracts a host object of the requested type from a polyglot value.
     * <p>
     * The returned object may be a direct GraalPy host object or the generated Java wrapper
     * recovered through {@link #HOST_OBJECT_MEMBER}.
     *
     * @param value The polyglot value to inspect.
     * @param targetType The required host object type.
     * @return The host object when it is assignable to {@code targetType}; otherwise {@code null}.
     */
    static @Nullable Object hostObject(@Nullable Value value, Class<?> targetType) {
        Object hostObject = rawHostObject(value);
        return targetType.isInstance(hostObject) ? hostObject : null;
    }

    /**
     * Extracts a host object of the requested type from a proxy object.
     * <p>
     * This method checks Micronaut's synthetic {@link #HOST_OBJECT_MEMBER} and verifies the
     * recovered host object before returning it.
     *
     * @param value The proxy object to inspect.
     * @param targetType The required host object type.
     * @return The host object when it is assignable to {@code targetType}; otherwise {@code null}.
     */
    static @Nullable Object hostObject(@Nullable ProxyObject value, Class<?> targetType) {
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
                if (hostObject instanceof HostObjectReference reference) {
                    return reference.value();
                }
                return hostObject;
            }
            if (!value.hasMembers() || !value.hasMember(HOST_OBJECT_MEMBER)) {
                return null;
            }
            Value hostReferenceValue = value.getMember(HOST_OBJECT_MEMBER);
            if (hostReferenceValue == null || !hostReferenceValue.isHostObject()) {
                return null;
            }
            Object hostReference = hostReferenceValue.asHostObject();
            return hostReference instanceof HostObjectReference reference ? reference.value() : null;
        } catch (UnsupportedOperationException e) {
            return null;
        }
    }

    private static @Nullable Object rawHostObject(@Nullable ProxyObject value) {
        try {
            if (value == null || !value.hasMember(HOST_OBJECT_MEMBER)) {
                return null;
            }
            Object hostReference = value.getMember(HOST_OBJECT_MEMBER);
            return hostReference instanceof HostObjectReference reference ? reference.value() : null;
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
     * {@link #HOST_OBJECT_MEMBER}.
     *
     * @param value The Python argument
     * @param targetType The Java parameter type
     * @return Whether the argument can be passed to the generated method
     */
    static boolean matchesArgument(Value value, Class<?> targetType) {
        Class<?> boxedType = ReflectionUtils.getWrapperType(targetType);
        if (GraalPyRuntimeUtil.isNone(value)) {
            return !targetType.isPrimitive();
        }
        if (boxedType == Object.class || boxedType == Value.class) {
            return true;
        }
        Object hostObject = ValueCoercible.hostObject(value, boxedType);
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
