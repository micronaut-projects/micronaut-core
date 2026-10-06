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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The definition of a curated Python module: the Java sources whose names it exports, in precedence order,
 * and how the names two sources both export are decided.
 *
 * <p>A clash, a name exported by more than one source for different targets, is decided by the first rule
 * that applies: an explicit {@link #prefer() preferred target} for the name; with
 * {@link ClashPolicy#PREFER_ANNOTATIONS}, the only annotation among the candidates; a
 * {@link #preferredPackages() preferred package} one of the candidates belongs to; then the
 * {@link #clashPolicy() clash policy}: an error with {@link ClashPolicy#FAIL}, else the first source.</p>
 *
 * @param module            The Python module name, such as {@code pyronaut.http}
 * @param documentation     The module documentation, shown by editors, or {@code null}
 * @param sources           The sources of the exported names, in precedence order
 * @param prefer            The explicit winners of clashes: Python name to the binary name of the winning type
 *                          (the declaring type for a static method or an enum constant)
 * @param preferredPackages The packages whose names win the clashes they take part in, in precedence order,
 *                          once an explicit preference and {@link ClashPolicy#PREFER_ANNOTATIONS} have not decided
 * @param clashPolicy       How the clashes that neither {@code prefer} nor {@code preferredPackages} decide are decided
 * @param exclude           The Python names left out of the module
 * @param replaces          Whether this contribution replaces the contributions of lower precedence to the
 *                          same module instead of merging with them
 * @param requiredArtifact  The coordinates of the artifact providing the first source, named in the error
 *                          reported when the module is imported without it, or {@code null}
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public record PythonModuleMapping(
    String module,
    @Nullable String documentation,
    List<Source> sources,
    Map<String, String> prefer,
    List<String> preferredPackages,
    ClashPolicy clashPolicy,
    Set<String> exclude,
    boolean replaces,
    @Nullable String requiredArtifact) {

    private static final Pattern MODULE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+");

    /**
     * Validates and copies the mapping.
     *
     * @param module            The Python module name
     * @param documentation     The module documentation
     * @param sources           The sources
     * @param prefer            The explicit clash winners
     * @param preferredPackages The preferred packages
     * @param clashPolicy       The clash policy
     * @param exclude           The excluded names
     * @param replaces          Whether the contribution replaces those of lower precedence
     * @param requiredArtifact  The artifact providing the first source
     */
    public PythonModuleMapping {
        Objects.requireNonNull(module, "module");
        if (!MODULE_NAME.matcher(module).matches()) {
            throw new IllegalArgumentException("Invalid Python module name [" + module + "]: a curated module is a dotted name of at least two identifiers, such as pyronaut.http");
        }
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Python module [" + module + "] declares no sources");
        }
        sources = List.copyOf(sources);
        prefer = Collections.unmodifiableMap(new LinkedHashMap<>(prefer == null ? Map.of() : prefer));
        preferredPackages = preferredPackages == null ? List.of() : List.copyOf(preferredPackages);
        clashPolicy = clashPolicy == null ? ClashPolicy.SOURCE_ORDER : clashPolicy;
        exclude = Collections.unmodifiableSet(new LinkedHashSet<>(exclude == null ? Set.of() : exclude));
    }

    /**
     * Starts the definition of a curated module.
     *
     * @param module The Python module name, such as {@code pyronaut.http}
     * @return The builder
     */
    public static Builder builder(String module) {
        return new Builder(module);
    }

    /**
     * How the clashes that no explicit preference decides are decided.
     */
    public enum ClashPolicy {
        /**
         * The source declared first wins.
         */
        SOURCE_ORDER,
        /**
         * An annotation wins when it is the only annotation among the candidates, before any preferred
         * package is considered: a facade over annotation and API packages exports the annotation a decorator
         * needs. Other clashes are decided by the preferred packages, then by the source declared first.
         */
        PREFER_ANNOTATIONS,
        /**
         * Any clash that no explicit preference decides is an error, so a new clash introduced by an upgrade
         * of a source is noticed instead of changing what a name means.
         */
        FAIL
    }

    /**
     * A source of the names a curated module exports.
     */
    public sealed interface Source {

        /**
         * Every public, top-level type of a Java package that is not internal (not annotated with
         * {@code @Internal}), or only those of the given kinds. Each is exported under its simple name.
         *
         * @param name  The Java package name
         * @param kinds The kinds of types to export, or empty for all of them
         */
        record JavaPackage(String name, Set<ClassIndex.TypeKind> kinds) implements Source {
            /**
             * @param name  The Java package name
             * @param kinds The kinds of types to export
             */
            public JavaPackage {
                Objects.requireNonNull(name, "name");
                kinds = kinds == null ? Set.of() : Set.copyOf(kinds);
            }

            /**
             * Every exported type of a package.
             *
             * @param name The Java package name
             */
            public JavaPackage(String name) {
                this(name, Set.of());
            }
        }

        /**
         * One Java type, exported under its simple name or an alias.
         *
         * @param binaryName The binary name of the type
         * @param alias      The Python name, or {@code null} for the simple name
         */
        record JavaType(String binaryName, @Nullable String alias) implements Source {
            /**
             * @param binaryName The binary name of the type
             * @param alias      The Python name
             */
            public JavaType {
                Objects.requireNonNull(binaryName, "binaryName");
            }
        }

        /**
         * The public static methods of a Java type, exported as module functions under their names.
         *
         * @param binaryName The binary name of the declaring type
         * @param include    The names of the methods to export, or empty for all of them
         */
        record StaticMethods(String binaryName, Set<String> include) implements Source {
            /**
             * @param binaryName The binary name of the declaring type
             * @param include    The names of the methods to export
             */
            public StaticMethods {
                Objects.requireNonNull(binaryName, "binaryName");
                include = Collections.unmodifiableSet(new LinkedHashSet<>(include == null ? Set.of() : include));
            }
        }

        /**
         * The constants of a Java type, exported as module attributes under their names: the constants of an
         * enum ({@code HttpStatus.CREATED}), or the public static final fields of a class or an interface
         * ({@code MediaType.APPLICATION_JSON}).
         *
         * @param binaryName The binary name of the type
         * @param include    The names of the constants to export, or empty for all of them
         */
        record Constants(String binaryName, Set<String> include) implements Source {
            /**
             * @param binaryName The binary name of the type
             * @param include    The names of the constants to export
             */
            public Constants {
                Objects.requireNonNull(binaryName, "binaryName");
                include = Collections.unmodifiableSet(new LinkedHashSet<>(include == null ? Set.of() : include));
            }
        }
    }

    /**
     * Builds a {@link PythonModuleMapping}.
     */
    public static final class Builder {
        private final String module;
        private @Nullable String documentation;
        private final List<Source> sources = new ArrayList<>();
        private final Map<String, String> prefer = new LinkedHashMap<>();
        private final List<String> preferredPackages = new ArrayList<>();
        private ClashPolicy clashPolicy = ClashPolicy.SOURCE_ORDER;
        private final Set<String> exclude = new LinkedHashSet<>();
        private boolean replaces;
        private @Nullable String requiredArtifact;

        private Builder(String module) {
            this.module = module;
        }

        /**
         * @param documentation The module documentation, shown by editors
         * @return This builder
         */
        public Builder documentation(@Nullable String documentation) {
            this.documentation = documentation;
            return this;
        }

        /**
         * Exports the public, top-level types of a Java package.
         *
         * @param name The Java package name
         * @return This builder
         */
        public Builder javaPackage(String name) {
            sources.add(new Source.JavaPackage(name));
            return this;
        }

        /**
         * Exports the public, top-level types of the given kinds of a Java package, such as only the annotations
         * of a package that also holds the types implementing them.
         *
         * @param name  The Java package name
         * @param kind  A kind of types to export
         * @param kinds More kinds of types to export
         * @return This builder
         */
        public Builder javaPackage(String name, ClassIndex.TypeKind kind, ClassIndex.TypeKind... kinds) {
            Set<ClassIndex.TypeKind> selected = new LinkedHashSet<>(List.of(kinds));
            selected.add(kind);
            sources.add(new Source.JavaPackage(name, selected));
            return this;
        }

        /**
         * Exports one Java type under its simple name.
         *
         * @param binaryName The binary name of the type
         * @return This builder
         */
        public Builder javaType(String binaryName) {
            sources.add(new Source.JavaType(binaryName, null));
            return this;
        }

        /**
         * Exports one Java type under an alias.
         *
         * @param binaryName The binary name of the type
         * @param alias      The Python name
         * @return This builder
         */
        public Builder javaType(String binaryName, String alias) {
            sources.add(new Source.JavaType(binaryName, alias));
            return this;
        }

        /**
         * Exports public static methods of a Java type as module functions.
         *
         * @param binaryName The binary name of the declaring type
         * @param include    The names of the methods, none for all
         * @return This builder
         */
        public Builder staticMethods(String binaryName, String... include) {
            sources.add(new Source.StaticMethods(binaryName, Set.of(include)));
            return this;
        }

        /**
         * Exports the constants of a Java type as module attributes: the constants of an enum, or the public
         * static final fields of a class or an interface.
         *
         * @param binaryName The binary name of the type
         * @param include    The names of the constants, none for all
         * @return This builder
         */
        public Builder constants(String binaryName, String... include) {
            sources.add(new Source.Constants(binaryName, Set.of(include)));
            return this;
        }

        /**
         * Decides the clash over a name explicitly.
         *
         * @param name       The Python name
         * @param binaryName The binary name of the winning type, or of the type declaring the winning static
         *                   method or enum constant
         * @return This builder
         */
        public Builder prefer(String name, String binaryName) {
            prefer.put(name, binaryName);
            return this;
        }

        /**
         * Decides every clash a type of the given package takes part in for that type.
         *
         * @param javaPackage The Java package name
         * @return This builder
         */
        public Builder preferPackage(String javaPackage) {
            preferredPackages.add(javaPackage);
            return this;
        }

        /**
         * @param clashPolicy How the clashes that no preference decides are decided
         * @return This builder
         */
        public Builder clashPolicy(ClashPolicy clashPolicy) {
            this.clashPolicy = clashPolicy;
            return this;
        }

        /**
         * Leaves names out of the module.
         *
         * @param names The Python names
         * @return This builder
         */
        public Builder exclude(String... names) {
            exclude.addAll(List.of(names));
            return this;
        }

        /**
         * Replaces the contributions of lower precedence to the same module, such as the built-in mapping a
         * module shipping its own mapper supersedes.
         *
         * @return This builder
         */
        public Builder replaces() {
            this.replaces = true;
            return this;
        }

        /**
         * @param coordinates The coordinates of the artifact providing the first source, such as
         *                    {@code io.micronaut.data:micronaut-data-model}
         * @return This builder
         */
        public Builder requiredArtifact(String coordinates) {
            this.requiredArtifact = coordinates;
            return this;
        }

        /**
         * @return The mapping
         */
        public PythonModuleMapping build() {
            return new PythonModuleMapping(module, documentation, sources, prefer, preferredPackages, clashPolicy, exclude, replaces, requiredArtifact);
        }
    }
}
