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
package io.micronaut.python.imports;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A curated module resolved against a class path: the names it exports and the targets they stand for.
 *
 * @param module          The Python module name
 * @param documentation   The module documentation, or {@code null}
 * @param members         The exported names, sorted, to their targets
 * @param clashes         The clashes decided while resolving, with their winners
 * @param active          Whether the module is available: the first source of a contribution is on the class path
 * @param inactiveReason  Why the module is not available, or {@code null} when it is
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public record ResolvedModule(
    String module,
    @Nullable String documentation,
    Map<String, Member> members,
    List<Clash> clashes,
    boolean active,
    @Nullable String inactiveReason) {

    /**
     * Copies the resolution.
     *
     * @param module         The Python module name
     * @param documentation  The module documentation
     * @param members        The exported names
     * @param clashes        The decided clashes
     * @param active         Whether the module is available
     * @param inactiveReason Why the module is not available
     */
    public ResolvedModule {
        Objects.requireNonNull(module, "module");
        members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
        clashes = List.copyOf(clashes);
    }

    /**
     * A member by Python name.
     *
     * @param name The Python name
     * @return The member
     */
    public Optional<Member> member(String name) {
        return Optional.ofNullable(members.get(name));
    }

    /**
     * The kinds of names a curated module exports.
     */
    public enum Kind {
        /**
         * An annotation type, applied as a decorator.
         */
        ANNOTATION,
        /**
         * An interface.
         */
        INTERFACE,
        /**
         * A class or a record.
         */
        CLASS,
        /**
         * An enum.
         */
        ENUM,
        /**
         * A public static method of a type, called as a module function.
         */
        STATIC_METHOD,
        /**
         * A constant of a type: an enum constant or a public static final field.
         */
        CONSTANT,
        /**
         * A curated module nested in this one.
         */
        MODULE
    }

    /**
     * A name a curated module exports.
     *
     * @param name       The Python name
     * @param kind       The kind of member
     * @param binaryName The binary name of the type, of the type declaring the static method or the
     *                   constant, or the name of a nested module
     * @param memberName The name of the static method or of the constant, else {@code null}
     */
    public record Member(String name, Kind kind, String binaryName, @Nullable String memberName) {

        /**
         * @return Whether the member is a Java type
         */
        public boolean isType() {
            return kind == Kind.ANNOTATION || kind == Kind.INTERFACE || kind == Kind.CLASS || kind == Kind.ENUM;
        }

        /**
         * @return What the member stands for: the binary name of a type, {@code Type#member} for a static
         * method or a constant, the name of a nested module
         */
        public String target() {
            return memberName == null ? binaryName : binaryName + '#' + memberName;
        }

        /**
         * @return The package of the type the member is or belongs to
         */
        public String packageName() {
            int lastDot = binaryName.lastIndexOf('.');
            return lastDot < 0 ? "" : binaryName.substring(0, lastDot);
        }
    }

    /**
     * A name more than one source exports for different targets.
     *
     * @param name       The Python name
     * @param candidates The candidates, in source order
     * @param winner     The candidate the module exports
     * @param reason     Which rule decided the clash
     */
    public record Clash(String name, List<Member> candidates, Member winner, String reason) {

        /**
         * @param name       The Python name
         * @param candidates The candidates
         * @param winner     The winner
         * @param reason     The deciding rule
         */
        public Clash {
            candidates = List.copyOf(candidates);
        }
    }
}
