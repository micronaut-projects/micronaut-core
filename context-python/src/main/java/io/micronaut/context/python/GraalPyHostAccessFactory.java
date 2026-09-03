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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;

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

    /** The Python module the date and time types are defined in. */
    private static final String DATETIME_MODULE = "datetime";

    /**
     * Builds a HostAccess instance and registers all TargetTypeMapping beans.
     *
     * @param mappings The discovered TargetTypeMapping beans
     * @return A HostAccess configured with custom target type mappings
     */
    @Singleton
    @Named(GraalPyRuntimeUtil.PYTHON)
    HostAccess hostAccess(Collection<TargetTypeMapping<?>> mappings) {
        HostAccess.Builder builder = HostAccess.newBuilder(HostAccess.ALL);
        PythonClassResolver pythonClassResolver = new PythonClassResolver(mappings);
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
        return builder.build();
    }

    private static void registerStandardLibraryMappings(HostAccess.Builder builder) {
        builder.targetTypeMapping(Value.class, LocalDate.class,
            value -> GraalPyRuntimeUtil.isPythonType(value, DATETIME_MODULE, "date"),
            GraalPyRuntimeUtil::convertLocalDate);
        builder.targetTypeMapping(Value.class, LocalTime.class,
            value -> GraalPyRuntimeUtil.isPythonType(value, DATETIME_MODULE, "time"),
            GraalPyRuntimeUtil::convertLocalTime);
        builder.targetTypeMapping(Value.class, LocalDateTime.class,
            value -> GraalPyRuntimeUtil.isPythonType(value, DATETIME_MODULE, "datetime"),
            GraalPyRuntimeUtil::convertLocalDateTime);
        builder.targetTypeMapping(Value.class, Duration.class,
            value -> GraalPyRuntimeUtil.isPythonType(value, DATETIME_MODULE, "timedelta"),
            GraalPyRuntimeUtil::convertDuration);
        builder.targetTypeMapping(Value.class, ZoneOffset.class,
            value -> GraalPyRuntimeUtil.isPythonType(value, DATETIME_MODULE, "timezone"),
            GraalPyRuntimeUtil::convertZoneOffset);
        builder.targetTypeMapping(Value.class, UUID.class,
            value -> GraalPyRuntimeUtil.isPythonType(value, "uuid", "UUID"),
            GraalPyRuntimeUtil::convertUuid);
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
                ValueCoercible host = ValueCoercible.hostObject(v);
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
                ValueCoercible host = ValueCoercible.hostObject(v);
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
                ValueCoercible host = ValueCoercible.hostObject(v);
                if (host != null && target.isInstance(host)) {
                    return true;
                }
                return findAssignableMapping(v, targetMappings, pythonClassResolver) != null;
            },
            v -> {
                ValueCoercible host = ValueCoercible.hostObject(v);
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
            v -> ValueCoercible.hostObject(v) != null || findMapping(v, pythonClassResolver) != null,
            v -> {
                ValueCoercible host = ValueCoercible.hostObject(v);
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
                ValueCoercible host = ValueCoercible.hostObject(v);
                return host != null && target.isInstance(host);
            },
            v -> target.cast(ValueCoercible.hostObject(v))
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
            value -> ValueCoercible.hostObject(value, target) != null,
            value -> target.cast(ValueCoercible.hostObject(value, target))
        );
    }

    private static void registerPythonClassMapping(HostAccess.Builder builder, PythonClassResolver pythonClassResolver) {
        builder.targetTypeMapping(
            Value.class,
            Class.class,
            v -> pythonClassResolver.findPythonClass(v) != null,
            v -> {
                Class<?> target = pythonClassResolver.findPythonClass(v);
                if (target == null) {
                    throw new IllegalArgumentException("Cannot resolve Python class to a generated Java stub");
                }
                return target;
            }
        );
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
        return pythonClassResolver.findClass(moduleName, simpleName);
    }

    private static String toGeneratedClassName(String moduleName, String simpleName) {
        String generatedSimpleName = simpleName.replace('.', '$');
        if (moduleName.equals(generatedSimpleName) || moduleName.endsWith("." + generatedSimpleName)) {
            return moduleName;
        }
        return moduleName + "." + generatedSimpleName;
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

    private static final class PythonClassResolver {
        private final Map<Class<?>, TargetTypeMapping<?>> mappingsByTargetType;
        private final Map<String, Class<?>> mappingsByClassName;
        private final Map<String, Optional<Class<?>>> uniqueMappingsBySimpleName;
        private final Map<PythonClassLookupKey, Optional<Class<?>>> resolvedClasses = new ConcurrentHashMap<>();

        private PythonClassResolver(Collection<TargetTypeMapping<?>> mappings) {
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
            if (key.moduleName() != null) {
                Class<?> exact = mappingsByClassName.get(toGeneratedClassName(key.moduleName(), key.simpleName()));
                if (exact != null) {
                    return exact;
                }
            }
            return uniqueMappingsBySimpleName.getOrDefault(key.simpleName(), Optional.empty()).orElse(null);
        }
    }

    private record PythonClassLookupKey(@Nullable String moduleName, String simpleName) {
    }

}
