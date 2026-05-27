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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.graal.Boxed;
import io.micronaut.core.reflect.ReflectionUtils;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;

import java.beans.Transient;

/**
 * A type that is coercible to a Truffle Value.
 */
public interface ValueCoercible extends Boxed<Value>, ProxyObject {
    String HOST_OBJECT_MEMBER = "__micronaut_value_coercible_host__";

    /**
     * Converts the type to a Truffle value.
     * @return The value
     */
    @NonNull Value asPolyglotValue();

    @Override
    default Value $unbox() {
        return asPolyglotValue();
    }

    @Override
    default @Nullable Object getMember(String key) {
        if (HOST_OBJECT_MEMBER.equals(key)) {
            return new HostObjectReference(this);
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

    @Override
    @Transient
    default Object getMemberKeys() {
        Value value = asPolyglotValue();
        if (value.hasMembers()) {
            return value.getMemberKeys().toArray(new String[0]);
        }
        return new String[0];
    }

    @Override
    default boolean hasMember(String key) {
        return HOST_OBJECT_MEMBER.equals(key) ||
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
        @Transient
        @Nullable String micronautValueCoercibleGetterPropertyName(String key);

        @Transient
        @Nullable String micronautValueCoercibleSetterPropertyName(String key);
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
            Value value = arguments[0];
            if (GraalPyRuntimeUtil.isNone(value)) {
                asPolyglotValue().putMember(propertyName, null);
            } else {
                asPolyglotValue().putMember(propertyName, value);
            }
            return null;
        };
    }

    @Override
    default void putMember(String key, Value value) {
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
    }

    record HostObjectReference(ValueCoercible value) {
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
        Object hostObject = hostObject(value);
        if (hostObject != null) {
            return boxedType.isInstance(hostObject);
        }
        if (boxedType == String.class) {
            return value.isString();
        }
        if (boxedType == Boolean.class) {
            return value.isBoolean();
        }
        if (Number.class.isAssignableFrom(boxedType)) {
            return value.isNumber();
        }
        if (boxedType == Character.class) {
            return value.isString() && value.asString().length() == 1;
        }
        return false;
    }

    private static @Nullable Object hostObject(Value value) {
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (!value.hasMembers() || !value.hasMember(HOST_OBJECT_MEMBER)) {
            return null;
        }
        Value hostReferenceValue = value.getMember(HOST_OBJECT_MEMBER);
        if (hostReferenceValue == null || !hostReferenceValue.isHostObject()) {
            return null;
        }
        Object hostReference = hostReferenceValue.asHostObject();
        if (hostReference instanceof HostObjectReference reference) {
            return reference.value();
        }
        return null;
    }
}
