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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Instance member names computed from the Java APIs referenced by a Python compilation.
 * Generated providers are singleton beans and services; neither registration nor lookup discovers
 * Java members at runtime. An entry also applies to implementations of its API type.
 *
 * @since 5.3.0
 */
@Internal
public interface PythonJavaInstanceMembersProvider {

    /** @return The registered API types and their member aliases */
    List<Entry> entries();

    /**
     * Collects generated entries, omitting optional API types absent from the runtime classpath.
     *
     * @param entries The entries; a generated factory returns {@code null} for a missing type
     * @return The available registrations
     */
    @UsedByGeneratedCode
    static List<Entry> available(@Nullable Entry... entries) {
        return Arrays.stream(entries).filter(Objects::nonNull).toList();
    }

    /**
     * @param type The public API type
     * @param members The aliases, including blocked ambiguous names
     */
    record Entry(Class<?> type, List<Member> members) {
    }

    /**
     * @param alias The Python member name
     * @param name The original Java member name; empty when this alias is ambiguous or excluded
     * @param property Whether the member is a read-only getter property
     */
    record Member(String alias, String name, boolean property) {
    }
}
