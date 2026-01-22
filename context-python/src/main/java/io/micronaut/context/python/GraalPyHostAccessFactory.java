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
import jakarta.inject.Singleton;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.NonNull;

import java.util.Collection;

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
    @NonNull
    HostAccess hostAccess(Collection<TargetTypeMapping<?>> mappings) {
        HostAccess.Builder builder = HostAccess.newBuilder(HostAccess.ALL);
        for (TargetTypeMapping<?> mapping : mappings) {
            register(builder, mapping);
        }
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
    private static <T> void register(HostAccess.Builder builder, TargetTypeMapping<T> mapping) {
        Class<T> target = mapping.targetType();
        String simpleName = target.getSimpleName();
        builder.<Value, T>targetTypeMapping(
            Value.class,
            target,
            v -> {
                if (v == null || v.isNull()) {
                    return false;
                }
                Value cls = v.getMember(CLASS_META);
                if (cls == null) {
                    return false;
                }
                String className = cls.getMetaSimpleName();
                return simpleName.equals(className);
            },
            mapping::convert
        );
    }
}
