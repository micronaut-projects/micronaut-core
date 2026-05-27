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

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Builds a HostAccess instance and registers all TargetTypeMapping beans.
     *
     * @param mappings The discovered TargetTypeMapping beans
     * @return A HostAccess configured with custom target type mappings
     */
    @Singleton
    @Named(GraalPyRuntimeUtil.PYTHON)
    @NonNull
    HostAccess hostAccess(Collection<TargetTypeMapping<?>> mappings) {
        HostAccess.Builder builder = HostAccess.newBuilder(HostAccess.ALL);
        Map<Class<?>, List<TargetTypeMapping<?>>> assignableMappings = new LinkedHashMap<>();
        for (TargetTypeMapping<?> mapping : mappings) {
            register(builder, mapping, mappings);
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
            registerAssignable(builder, entry.getKey(), entry.getValue(), mappings);
        }
        registerValueCoercibleHostMapping(builder, ValueCoercible.class);
        registerValueCoercibleHostMapping(builder, Throwable.class);
        registerValueCoercibleHostMapping(builder, Exception.class);
        registerValueCoercibleHostMapping(builder, RuntimeException.class);
        registerPythonClassMapping(builder, mappings);
        registerObjectMapping(builder, mappings);
        return builder.build();
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
                                     Collection<TargetTypeMapping<?>> mappings) {
        Class<T> target = mapping.targetType();
        builder.<Value, T>targetTypeMapping(
            Value.class,
            target,
            v -> {
                ValueCoercible host = valueCoercibleHost(v);
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
                return target.equals(findPythonClass(cls, mappings));
            },
            v -> {
                ValueCoercible host = valueCoercibleHost(v);
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
                                           Collection<TargetTypeMapping<?>> allMappings) {
        builder.targetTypeMapping(
            Value.class,
            (Class) target,
            v -> {
                ValueCoercible host = valueCoercibleHost(v);
                if (host != null && target.isInstance(host)) {
                    return true;
                }
                return findAssignableMapping(v, targetMappings, allMappings) != null;
            },
            v -> {
                ValueCoercible host = valueCoercibleHost(v);
                if (host != null && target.isInstance(host)) {
                    return target.cast(host);
                }
                TargetTypeMapping<?> mapping = findAssignableMapping(v, targetMappings, allMappings);
                if (mapping == null) {
                    throw new IllegalArgumentException("Cannot resolve Python value to " + target.getName());
                }
                return target.cast(mapping.convert(v));
            }
        );
        registerValueCoercibleAssignableHostMapping(builder, target);
        registerProxyHostMapping(builder, target);
    }

    private static void registerObjectMapping(HostAccess.Builder builder, Collection<TargetTypeMapping<?>> mappings) {
        builder.<Value, Object>targetTypeMapping(
            Value.class,
            Object.class,
            v -> valueCoercibleHost(v) != null || findMapping(v, mappings) != null,
            v -> {
                ValueCoercible host = valueCoercibleHost(v);
                if (host != null) {
                    return host;
                }
                TargetTypeMapping<?> mapping = findMapping(v, mappings);
                return mapping == null ? v : mapping.convert(v);
            }
        );
    }

    private static <T> void registerValueCoercibleHostMapping(HostAccess.Builder builder, Class<T> target) {
        builder.<Value, T>targetTypeMapping(
            Value.class,
            target,
            v -> {
                ValueCoercible host = valueCoercibleHost(v);
                return host != null && target.isInstance(host);
            },
            v -> target.cast(valueCoercibleHost(v))
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
            value -> valueCoercibleHost(value, target) != null,
            value -> target.cast(valueCoercibleHost(value, target))
        );
    }

    @SuppressWarnings("rawtypes")
    private static void registerPythonClassMapping(HostAccess.Builder builder, Collection<TargetTypeMapping<?>> mappings) {
        builder.<Value, Class>targetTypeMapping(
            Value.class,
            Class.class,
            v -> findPythonClass(v, mappings) != null,
            v -> {
                Class<?> target = findPythonClass(v, mappings);
                if (target == null) {
                    throw new IllegalArgumentException("Cannot resolve Python class to a generated Java stub");
                }
                return target;
            }
        );
    }

    private static @Nullable TargetTypeMapping<?> findMapping(@Nullable Value value, Collection<TargetTypeMapping<?>> mappings) {
        if (value == null || value.isNull() || value.isHostObject() || !value.hasMembers()) {
            return null;
        }
        Value cls = value.getMember(CLASS_META);
        if (cls == null) {
            return null;
        }
        Class<?> pythonClass = findPythonClass(cls, mappings);
        if (pythonClass == null) {
            return null;
        }
        for (TargetTypeMapping<?> mapping : mappings) {
            if (mapping.targetType().equals(pythonClass)) {
                return mapping;
            }
        }
        return null;
    }

    private static @Nullable TargetTypeMapping<?> findAssignableMapping(@Nullable Value value,
                                                                       List<TargetTypeMapping<?>> targetMappings,
                                                                       Collection<TargetTypeMapping<?>> allMappings) {
        if (value == null || value.isNull() || value.isHostObject() || !value.hasMembers()) {
            return null;
        }
        Value cls = value.getMember(CLASS_META);
        if (cls == null) {
            return null;
        }
        Class<?> pythonClass = findPythonClass(cls, allMappings);
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

    private static @Nullable Class<?> findPythonClass(@Nullable Value value, Collection<TargetTypeMapping<?>> mappings) {
        if (value == null || value.isNull() || value.isHostObject() || !value.hasMembers() || !value.hasMember("__mro__")) {
            return null;
        }
        String className = stringMember(value, "__name__");
        String qualifiedName = stringMember(value, "__qualname__");
        String moduleName = stringMember(value, "__module__");
        String simpleName = qualifiedName == null || qualifiedName.isBlank() ? className : qualifiedName;
        if (simpleName == null || simpleName.isBlank() || simpleName.contains("<locals>")) {
            return null;
        }
        if (moduleName != null && !moduleName.isBlank()) {
            Class<?> exact = findMappingForClassName(toGeneratedClassName(moduleName, simpleName), mappings);
            if (exact != null) {
                return exact;
            }
        }
        return findUniqueMappingForSimpleName(simpleName, mappings);
    }

    private static String toGeneratedClassName(String moduleName, String simpleName) {
        String generatedSimpleName = simpleName.replace('.', '$');
        if (moduleName.equals(generatedSimpleName) || moduleName.endsWith("." + generatedSimpleName)) {
            return moduleName;
        }
        return moduleName + "." + generatedSimpleName;
    }

    private static @Nullable Class<?> findMappingForClassName(String className, Collection<TargetTypeMapping<?>> mappings) {
        for (TargetTypeMapping<?> mapping : mappings) {
            Class<?> targetType = mapping.targetType();
            if (targetType.getName().equals(className)) {
                return targetType;
            }
        }
        return null;
    }

    private static @Nullable Class<?> findUniqueMappingForSimpleName(String simpleName, Collection<TargetTypeMapping<?>> mappings) {
        Class<?> match = null;
        for (TargetTypeMapping<?> mapping : mappings) {
            Class<?> targetType = mapping.targetType();
            if (!targetType.getSimpleName().equals(simpleName)) {
                continue;
            }
            if (match != null && match != targetType) {
                return null;
            }
            match = targetType;
        }
        return match;
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

    private static @Nullable ValueCoercible valueCoercibleHost(@Nullable Value value) {
        if (value == null || value.isNull() || value.isHostObject() || !value.hasMembers() || !value.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            return null;
        }
        Value hostReferenceValue = value.getMember(ValueCoercible.HOST_OBJECT_MEMBER);
        if (hostReferenceValue == null || !hostReferenceValue.isHostObject()) {
            return null;
        }
        Object hostReference = hostReferenceValue.asHostObject();
        if (hostReference instanceof ValueCoercible.HostObjectReference reference) {
            return reference.value();
        }
        return null;
    }

    private static @Nullable ValueCoercible valueCoercibleHost(@Nullable ProxyObject value, Class<?> target) {
        if (value == null || !value.hasMember(ValueCoercible.HOST_OBJECT_MEMBER)) {
            return null;
        }
        Object hostReference = value.getMember(ValueCoercible.HOST_OBJECT_MEMBER);
        if (hostReference instanceof ValueCoercible.HostObjectReference reference && target.isInstance(reference.value())) {
            return reference.value();
        }
        return null;
    }
}
