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
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.python.imports.ClassIndex.TypeInfo;
import io.micronaut.python.imports.PythonModuleMapping.ClashPolicy;
import io.micronaut.python.imports.PythonModuleMapping.Source;
import io.micronaut.python.imports.ResolvedModule.Clash;
import io.micronaut.python.imports.ResolvedModule.Kind;
import io.micronaut.python.imports.ResolvedModule.Member;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The curated Python modules the {@link PythonImportMapper mappers} on a class path contribute, and their
 * resolution against a {@link ClassIndex}.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Experimental
public final class PythonImportMappings {

    private static final PythonImportMappings EMPTY = new PythonImportMappings(Map.of());
    private static final Set<String> PYTHON_KEYWORDS = Set.of(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
        "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    );
    private static final Set<String> SKIPPED_TYPE_NAMES = Set.of("package-info", "module-info");

    /**
     * The contributions to each module, highest precedence first, after replacement.
     */
    private final Map<String, List<PythonModuleMapping>> contributions;
    private final Set<String> namespaces;

    private PythonImportMappings(Map<String, List<PythonModuleMapping>> contributions) {
        this.contributions = contributions;
        Set<String> parents = new TreeSet<>();
        for (String module : contributions.keySet()) {
            int dot = module.indexOf('.');
            while (dot > 0) {
                parents.add(module.substring(0, dot));
                dot = module.indexOf('.', dot + 1);
            }
        }
        this.namespaces = Collections.unmodifiableSet(parents);
    }

    /**
     * The mappings of the mappers a class loader provides as services.
     *
     * @param classLoader The class loader
     * @return The mappings
     */
    public static PythonImportMappings load(@Nullable ClassLoader classLoader) {
        List<PythonImportMapper> mappers = new ArrayList<>();
        SoftServiceLoader.load(PythonImportMapper.class, classLoader).collectAll(mappers);
        return of(mappers);
    }

    /**
     * The mappings of the given mappers.
     *
     * @param mappers The mappers
     * @return The mappings
     */
    public static PythonImportMappings of(List<? extends PythonImportMapper> mappers) {
        if (mappers.isEmpty()) {
            return EMPTY;
        }
        List<PythonImportMapper> ordered = new ArrayList<>(mappers);
        OrderUtil.sort(ordered);
        Map<String, List<PythonModuleMapping>> contributions = new TreeMap<>();
        for (PythonImportMapper mapper : ordered) {
            for (PythonModuleMapping mapping : mapper.getMappings()) {
                contributions.computeIfAbsent(mapping.module(), k -> new ArrayList<>()).add(mapping);
            }
        }
        contributions.replaceAll((module, mappings) -> {
            for (int i = 0; i < mappings.size(); i++) {
                if (mappings.get(i).replaces()) {
                    return List.copyOf(mappings.subList(0, i + 1));
                }
            }
            return List.copyOf(mappings);
        });
        return new PythonImportMappings(Collections.unmodifiableMap(contributions));
    }

    /**
     * @return Whether no module is mapped
     */
    public boolean isEmpty() {
        return contributions.isEmpty();
    }

    /**
     * @return The names of the mapped modules, sorted
     */
    public Set<String> modules() {
        return contributions.keySet();
    }

    /**
     * Whether a name is a mapped module.
     *
     * @param name The Python module name
     * @return Whether it is mapped
     */
    public boolean isModule(String name) {
        return contributions.containsKey(name);
    }

    /**
     * Whether a name is a package enclosing mapped modules, such as {@code pyronaut} for {@code pyronaut.http}.
     *
     * @param name The Python module name
     * @return Whether mapped modules live under it
     */
    public boolean isNamespace(String name) {
        return namespaces.contains(name);
    }

    /**
     * The contributions to a module, highest precedence first.
     *
     * @param module The Python module name
     * @return The contributions, empty when the module is not mapped
     */
    public List<PythonModuleMapping> contributions(String module) {
        return contributions.getOrDefault(module, List.of());
    }

    /**
     * A digest of every mapping, which changes whenever what any module may resolve to changes.
     *
     * @return The hexadecimal digest
     */
    public String fingerprint() {
        StringBuilder description = new StringBuilder();
        contributions.forEach((module, mappings) -> {
            for (PythonModuleMapping mapping : mappings) {
                description.append(mapping).append('\n');
            }
        });
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(description.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Starts resolving modules against a class index. The resolver caches what it resolves, so one is used
     * per compilation.
     *
     * @param index The class index
     * @return The resolver
     */
    public Resolver resolver(ClassIndex index) {
        return new Resolver(index);
    }

    private static String inactiveReason(String module, PythonModuleMapping mapping) {
        Source first = mapping.sources().getFirst();
        String what = switch (first) {
            case Source.JavaPackage javaPackage -> "the Java package [" + javaPackage.name() + "]";
            case Source.JavaType javaType -> "the Java type [" + javaType.binaryName() + "]";
            case Source.StaticMethods staticMethods -> "the Java type [" + staticMethods.binaryName() + "]";
            case Source.Constants constants -> "the Java type [" + constants.binaryName() + "]";
        };
        String artifact = mapping.requiredArtifact() != null ? ": add the dependency " + mapping.requiredArtifact() : "";
        return "Python module [" + module + "] requires " + what + ", which is not on the class path" + artifact;
    }

    private static boolean isExported(TypeInfo type) {
        return type.publicType() && !type.internal() && !type.nested() && !SKIPPED_TYPE_NAMES.contains(type.simpleName());
    }

    private static Kind kindOf(TypeInfo type) {
        return switch (type.kind()) {
            case ANNOTATION -> Kind.ANNOTATION;
            case ENUM -> Kind.ENUM;
            case INTERFACE -> Kind.INTERFACE;
            case CLASS -> Kind.CLASS;
        };
    }

    /**
     * The Python spelling of a Java name: a Python keyword gains a trailing underscore.
     *
     * @param javaName The Java name
     * @return The Python name
     */
    public static String pythonName(String javaName) {
        return PYTHON_KEYWORDS.contains(javaName) ? javaName + '_' : javaName;
    }

    /**
     * Resolves the mapped modules against one class index, caching every module it resolves.
     */
    public final class Resolver {
        private final ClassIndex index;
        private final Map<String, ResolvedModule> resolved = new LinkedHashMap<>();
        private final Set<String> resolving = new LinkedHashSet<>();

        private Resolver(ClassIndex index) {
            this.index = index;
        }

        /**
         * @return The mappings this resolver resolves
         */
        public PythonImportMappings mappings() {
            return PythonImportMappings.this;
        }

        /**
         * Resolves a module.
         *
         * @param module The Python module name
         * @return The resolved module, empty when the name is not mapped; a mapped module whose sources are
         * missing resolves {@link ResolvedModule#active() inactive}
         * @throws PythonImportMappingException when a clash is left undecided
         */
        public Optional<ResolvedModule> resolve(String module) {
            List<PythonModuleMapping> mappings = contributions.get(module);
            if (mappings == null) {
                return Optional.empty();
            }
            ResolvedModule cached = resolved.get(module);
            if (cached != null) {
                return Optional.of(cached);
            }
            if (!resolving.add(module)) {
                throw new PythonImportMappingException("Python module [" + module + "] is nested in itself");
            }
            try {
                ResolvedModule result = resolveModule(module, mappings);
                resolved.put(module, result);
                return Optional.of(result);
            } finally {
                resolving.remove(module);
            }
        }

        private ResolvedModule resolveModule(String module, List<PythonModuleMapping> mappings) {
            Map<String, Member> members = new LinkedHashMap<>();
            Map<String, PythonModuleMapping> owners = new LinkedHashMap<>();
            List<Clash> clashes = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            String documentation = null;
            String inactiveReason = null;
            boolean active = false;
            for (PythonModuleMapping mapping : mappings) {
                if (!isAvailable(mapping.sources().getFirst())) {
                    if (inactiveReason == null) {
                        inactiveReason = inactiveReason(module, mapping);
                    }
                    continue;
                }
                active = true;
                if (documentation == null) {
                    documentation = mapping.documentation();
                }
                Map<String, Member> contributed = resolveContribution(module, mapping, clashes, errors);
                for (Member member : contributed.values()) {
                    Member existing = members.get(member.name());
                    if (existing == null) {
                        members.put(member.name(), member);
                        owners.put(member.name(), mapping);
                    } else if (!existing.target().equals(member.target())) {
                        PythonModuleMapping owner = Objects.requireNonNull(owners.get(member.name()));
                        if (owner.prefer().containsKey(member.name())) {
                            clashes.add(new Clash(member.name(), List.of(existing, member), existing, "prefer of the contribution of higher precedence"));
                        } else if (mapping.prefer().containsKey(member.name())) {
                            clashes.add(new Clash(member.name(), List.of(existing, member), member, "prefer of the contribution of lower precedence"));
                            members.put(member.name(), member);
                            owners.put(member.name(), mapping);
                        } else {
                            errors.add("Python module [" + module + "] has contributions binding [" + member.name() + "] to both ["
                                + existing.target() + "] and [" + member.target() + "]: declare prefer(\"" + member.name()
                                + "\", ...) in one of them, or let one replace the other");
                        }
                    }
                }
            }
            if (active) {
                addNestedModules(module, members, clashes);
            }
            if (!errors.isEmpty()) {
                throw new PythonImportMappingException(String.join(System.lineSeparator(), errors));
            }
            Map<String, Member> sorted = new TreeMap<>(members);
            return new ResolvedModule(module, documentation, sorted, clashes, active, active ? null : inactiveReason);
        }

        private void addNestedModules(String module, Map<String, Member> members, List<Clash> clashes) {
            String prefix = module + '.';
            for (String candidate : contributions.keySet()) {
                if (!candidate.startsWith(prefix) || candidate.indexOf('.', prefix.length()) >= 0) {
                    continue;
                }
                String name = candidate.substring(prefix.length());
                Member nested = new Member(name, Kind.MODULE, candidate, null);
                Member existing = members.get(name);
                if (existing != null) {
                    clashes.add(new Clash(name, List.of(existing, nested), existing, "a member wins over a nested module"));
                } else if (resolve(candidate).map(ResolvedModule::active).orElse(false)) {
                    members.put(name, nested);
                }
            }
        }

        private Map<String, Member> resolveContribution(String module, PythonModuleMapping mapping, List<Clash> clashes, List<String> errors) {
            Map<String, List<Member>> candidates = new LinkedHashMap<>();
            for (Source source : mapping.sources()) {
                for (Member member : membersOf(source)) {
                    if (mapping.exclude().contains(member.name())) {
                        continue;
                    }
                    List<Member> named = candidates.computeIfAbsent(member.name(), k -> new ArrayList<>());
                    if (named.stream().noneMatch(m -> m.target().equals(member.target()))) {
                        named.add(member);
                    }
                }
            }
            Map<String, Member> members = new LinkedHashMap<>();
            candidates.forEach((name, named) -> {
                if (named.size() == 1) {
                    members.put(name, named.getFirst());
                    return;
                }
                Clash clash = decide(module, mapping, name, named, errors);
                if (clash != null) {
                    clashes.add(clash);
                    members.put(name, clash.winner());
                }
            });
            return members;
        }

        private @Nullable Clash decide(String module, PythonModuleMapping mapping, String name, List<Member> candidates, List<String> errors) {
            String preferred = mapping.prefer().get(name);
            if (preferred != null) {
                for (Member candidate : candidates) {
                    if (candidate.binaryName().equals(preferred) || candidate.target().equals(preferred)) {
                        return new Clash(name, candidates, candidate, "prefer");
                    }
                }
                errors.add("Python module [" + module + "] prefers [" + preferred + "] for [" + name + "], which is none of its candidates "
                    + targets(candidates));
                return null;
            }
            ClashPolicy policy = mapping.clashPolicy();
            if (policy == ClashPolicy.PREFER_ANNOTATIONS) {
                List<Member> annotations = candidates.stream().filter(m -> m.kind() == Kind.ANNOTATION).toList();
                if (annotations.size() == 1) {
                    return new Clash(name, candidates, annotations.getFirst(), "PREFER_ANNOTATIONS");
                }
            }
            for (String preferredPackage : mapping.preferredPackages()) {
                for (Member candidate : candidates) {
                    if (candidate.packageName().equals(preferredPackage)) {
                        return new Clash(name, candidates, candidate, "preferPackage " + preferredPackage);
                    }
                }
            }
            if (policy == ClashPolicy.FAIL) {
                errors.add("Python module [" + module + "] exports [" + name + "] from more than one source " + targets(candidates)
                    + ": declare prefer(\"" + name + "\", ...) or preferPackage(...) to choose one, or exclude(\"" + name + "\")");
                return null;
            }
            return new Clash(name, candidates, candidates.getFirst(), "SOURCE_ORDER");
        }

        private String targets(List<Member> candidates) {
            return candidates.stream().map(Member::target).toList().toString();
        }

        private boolean isAvailable(Source source) {
            return switch (source) {
                case Source.JavaPackage javaPackage -> index.types(javaPackage.name()).stream()
                    .anyMatch(type -> isExported(type) && (javaPackage.kinds().isEmpty() || javaPackage.kinds().contains(type.kind())));
                case Source.JavaType javaType -> index.type(javaType.binaryName()).isPresent();
                case Source.StaticMethods staticMethods -> index.type(staticMethods.binaryName()).isPresent();
                case Source.Constants constants -> index.type(constants.binaryName()).isPresent();
            };
        }

        private List<Member> membersOf(Source source) {
            List<Member> members = new ArrayList<>();
            switch (source) {
                case Source.JavaPackage javaPackage -> {
                    for (TypeInfo type : index.types(javaPackage.name())) {
                        if (isExported(type) && (javaPackage.kinds().isEmpty() || javaPackage.kinds().contains(type.kind()))) {
                            members.add(new Member(pythonName(type.simpleName()), kindOf(type), type.binaryName(), null));
                        }
                    }
                }
                case Source.JavaType javaType -> index.type(javaType.binaryName()).ifPresent(type ->
                    members.add(new Member(javaType.alias() != null ? javaType.alias() : pythonName(type.simpleName()), kindOf(type), type.binaryName(), null)));
                case Source.StaticMethods staticMethods -> {
                    if (index.type(staticMethods.binaryName()).isPresent()) {
                        for (String method : new LinkedHashSet<>(index.staticMethods(staticMethods.binaryName()))) {
                            if (staticMethods.include().isEmpty() || staticMethods.include().contains(method)) {
                                members.add(new Member(pythonName(method), Kind.STATIC_METHOD, staticMethods.binaryName(), method));
                            }
                        }
                    }
                }
                case Source.Constants constants -> {
                    for (String constant : index.constants(constants.binaryName())) {
                        if (constants.include().isEmpty() || constants.include().contains(constant)) {
                            members.add(new Member(pythonName(constant), Kind.CONSTANT, constants.binaryName(), constant));
                        }
                    }
                }
            }
            return members;
        }
    }
}
