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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-context lookup of compiler-registered names, leaving invocation to GraalPy host interop.
 *
 * @since 5.3.0
 */
@Internal
public final class PythonJavaInstanceMembers {
    private final List<PythonJavaInstanceMembersProvider.Entry> entries;
    // ClassValue does not keep the receiver's class loader alive.
    private final ClassValue<Map<String, PythonJavaInstanceMembersProvider.Member>> aliases = new ClassValue<>() {
        @Override
        protected Map<String, PythonJavaInstanceMembersProvider.Member> computeValue(Class<?> type) {
            Map<String, PythonJavaInstanceMembersProvider.Member> result = new HashMap<>();
            for (var entry : entries) {
                if (entry.type().isAssignableFrom(type)) {
                    for (var member : entry.members()) {
                        result.merge(member.alias(), member, (first, second) -> first.equals(second)
                            ? first : new PythonJavaInstanceMembersProvider.Member(first.alias(), "", false));
                    }
                }
            }
            return Map.copyOf(result);
        }
    };

    PythonJavaInstanceMembers(Collection<PythonJavaInstanceMembersProvider> providers) {
        entries = providers.stream().flatMap(provider -> provider.entries().stream()).toList();
    }

    /**
     * @param receiver The Java instance
     * @param name The Python member name
     * @return The original method name, or {@code null}
     */
    @UsedByGeneratedCode
    public @Nullable String methodAlias(Object receiver, String name) {
        var member = aliases.get(receiver.getClass()).get(name);
        return member != null && !member.property() && !member.name().isEmpty() ? member.name() : null;
    }

    /**
     * @param receiver The Java instance
     * @param name The Python member name
     * @return The original zero-argument getter name, or {@code null}
     */
    @UsedByGeneratedCode
    public @Nullable String propertyGetter(Object receiver, String name) {
        var member = aliases.get(receiver.getClass()).get(name);
        return member != null && member.property() && !member.name().isEmpty() ? member.name() : null;
    }
}
