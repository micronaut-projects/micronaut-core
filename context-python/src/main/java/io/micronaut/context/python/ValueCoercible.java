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
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.Nullable;

import java.beans.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

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
        Field field = findJavaField(key);
        if (field != null) {
            try {
                return field.get(this);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot access field [" + key + "]", e);
            }
        }
        if (hasJavaMethod(key)) {
            return (ProxyExecutable) arguments -> invokeJavaMethod(this, key, arguments);
        }
        return null;
    }

    @Override
    @Transient
    default Object getMemberKeys() {
        Set<String> keys = new LinkedHashSet<>();
        Value value = asPolyglotValue();
        if (value.hasMembers()) {
            keys.addAll(value.getMemberKeys());
        }
        Arrays.stream(this.getClass().getFields())
            .map(Field::getName)
            .forEach(keys::add);
        Arrays.stream(this.getClass().getMethods())
            .filter(ValueCoercible::isProxyVisibleMethod)
            .map(Method::getName)
            .forEach(keys::add);
        return keys.toArray(new String[0]);
    }

    @Override
    default boolean hasMember(String key) {
        return HOST_OBJECT_MEMBER.equals(key) || asPolyglotValue().hasMember(key) || findJavaField(key) != null || hasJavaMethod(key);
    }

    @Override
    default void putMember(String key, Value value) {
        if (trySetJavaField(key, value) || tryInvokeJavaSetter(key, value)) {
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
    }

    private @Nullable Field findJavaField(String key) {
        try {
            Field field = this.getClass().getField(key);
            return Modifier.isPublic(field.getModifiers()) ? field : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private boolean hasJavaMethod(String key) {
        return hasProxyVisibleJavaMethod(this, key);
    }

    static boolean hasProxyVisibleJavaMethod(Object target, String key) {
        return Arrays.stream(target.getClass().getMethods())
            .anyMatch(method -> method.getName().equals(key) && isProxyVisibleMethod(method));
    }

    static boolean isProxyVisibleMethod(Method method) {
        return Modifier.isPublic(method.getModifiers()) && method.getDeclaringClass() != Object.class;
    }

    static @Nullable Object invokeJavaMethod(Object target, String key, Value[] arguments) {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(key) || !isProxyVisibleMethod(method) || method.getParameterCount() != arguments.length) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target, convertArguments(arguments, method.getParameterTypes()));
            } catch (IllegalArgumentException e) {
                // Try another overload.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot invoke method [" + key + "]", e);
            }
        }
        throw new IllegalArgumentException("No compatible method [" + key + "] found");
    }

    private boolean trySetJavaField(String key, Value value) {
        Field field = findJavaField(key);
        if (field == null) {
            return false;
        }
        try {
            field.set(this, convertArgument(value, field.getType()));
            return true;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot set field [" + key + "]", e);
        }
    }

    private boolean tryInvokeJavaSetter(String key, Value value) {
        if (key.isEmpty()) {
            return false;
        }
        String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
        for (Method method : this.getClass().getMethods()) {
            if (!method.getName().equals(setterName) || !isProxyVisibleMethod(method) || method.getParameterCount() != 1) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(this, convertArgument(value, method.getParameterTypes()[0]));
                return true;
            } catch (IllegalArgumentException e) {
                // Try another overload.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot invoke setter [" + setterName + "]", e);
            }
        }
        return false;
    }

    private static Object[] convertArguments(Value[] arguments, Class<?>[] parameterTypes) {
        Object[] converted = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            converted[i] = convertArgument(arguments[i], parameterTypes[i]);
        }
        return converted;
    }

    private static @Nullable Object convertArgument(Value value, Class<?> targetType) {
        if (Value.class.equals(targetType)) {
            return value;
        }
        if (GraalPyRuntimeUtil.isNone(value)) {
            return null;
        }
        if (value.isHostObject()) {
            Object hostObject = value.asHostObject();
            if (targetType.isInstance(hostObject)) {
                return hostObject;
            }
        }
        return GraalPyRuntimeUtil.convertValue(value, targetType);
    }

    record HostObjectReference(ValueCoercible value) {
    }
}
