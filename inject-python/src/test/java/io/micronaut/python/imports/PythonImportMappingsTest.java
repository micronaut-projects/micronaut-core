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

import io.micronaut.python.imports.ClassIndex.TypeKind;
import io.micronaut.python.imports.PythonModuleMapping.ClashPolicy;
import io.micronaut.python.imports.ResolvedModule.Kind;
import io.micronaut.python.imports.ResolvedModule.Member;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonImportMappingsTest {

    private final FakeIndex index = new FakeIndex()
        .annotation("io.micronaut.http.annotation.Get")
        .annotation("io.micronaut.http.annotation.Controller")
        .type("io.micronaut.http.HttpResponse", TypeKind.INTERFACE)
        .type("io.micronaut.http.HttpStatus", TypeKind.ENUM)
        .type("io.micronaut.http.HttpMethod", TypeKind.ENUM)
        .type("io.micronaut.http.HttpResponse$Nested", TypeKind.CLASS)
        .type("io.micronaut.http.DefaultInternalThing", TypeKind.CLASS, true, true)
        .type("io.micronaut.http.PackagePrivate", TypeKind.CLASS, false, false)
        .statics("io.micronaut.http.HttpResponse", "ok", "ok", "notFound", "status")
        .constants("io.micronaut.http.HttpStatus", "OK", "CREATED", "NOT_FOUND")
        .constants("io.micronaut.http.HttpMethod", "GET", "POST")
        .annotation("jakarta.inject.Qualifier")
        .annotation("jakarta.inject.Singleton")
        .type("io.micronaut.context.Qualifier", TypeKind.INTERFACE)
        .type("io.micronaut.context.ApplicationContext", TypeKind.INTERFACE)
        .annotation("jakarta.persistence.Id")
        .annotation("jakarta.persistence.Entity")
        .annotation("io.micronaut.data.annotation.Id")
        .annotation("io.micronaut.data.annotation.MappedEntity")
        .annotation("io.micronaut.data.annotation.Query")
        .type("jakarta.persistence.Query", TypeKind.INTERFACE)
        .type("io.micronaut.http.MediaType", TypeKind.CLASS)
        .constants("io.micronaut.http.MediaType", "APPLICATION_JSON", "TEXT_PLAIN")
        .annotation("io.micronaut.http.client.annotation.Client");

    @Test
    void resolvesPackagesStaticsAndConstants() {
        ResolvedModule http = resolve(mappings(http()), "pyronaut.http");

        assertTrue(http.active());
        assertEquals("HTTP.", http.documentation());
        assertEquals(new Member("Get", Kind.ANNOTATION, "io.micronaut.http.annotation.Get", null), http.members().get("Get"));
        assertEquals(Kind.INTERFACE, http.members().get("HttpResponse").kind());
        assertEquals(Kind.ENUM, http.members().get("HttpStatus").kind());
        assertEquals(new Member("ok", Kind.STATIC_METHOD, "io.micronaut.http.HttpResponse", "ok"), http.members().get("ok"));
        assertEquals("io.micronaut.http.HttpStatus#CREATED", http.members().get("CREATED").target());
        assertFalse(http.members().containsKey("GET"), "only the HttpStatus constants were requested");
        // nested, internal and non-public types are not exported
        assertFalse(http.members().containsKey("Nested"));
        assertFalse(http.members().containsKey("DefaultInternalThing"));
        assertFalse(http.members().containsKey("PackagePrivate"));
        assertEquals(List.copyOf(http.members().keySet()), http.members().keySet().stream().sorted().toList());
        assertTrue(http.clashes().isEmpty());
    }

    @Test
    void includeFiltersStaticsAndConstants() {
        ResolvedModule http = resolve(mappings(PythonModuleMapping.builder("pyronaut.http")
            .javaPackage("io.micronaut.http.annotation")
            .staticMethods("io.micronaut.http.HttpResponse", "ok")
            .constants("io.micronaut.http.HttpMethod", "GET")
            .build()), "pyronaut.http");

        assertEquals(List.of("Controller", "GET", "Get", "ok"), List.copyOf(http.members().keySet()));
    }

    @Test
    void preferAnnotationsDecidesAnnotationAgainstType() {
        ResolvedModule inject = resolve(mappings(PythonModuleMapping.builder("pyronaut.inject")
            .javaPackage("io.micronaut.context")
            .javaPackage("jakarta.inject")
            .clashPolicy(ClashPolicy.PREFER_ANNOTATIONS)
            .build()), "pyronaut.inject");

        assertEquals("jakarta.inject.Qualifier", inject.members().get("Qualifier").binaryName());
        assertEquals(1, inject.clashes().size());
        assertEquals("PREFER_ANNOTATIONS", inject.clashes().getFirst().reason());
    }

    @Test
    void sourceOrderDecidesByDefault() {
        ResolvedModule inject = resolve(mappings(PythonModuleMapping.builder("pyronaut.inject")
            .javaPackage("io.micronaut.context")
            .javaPackage("jakarta.inject")
            .build()), "pyronaut.inject");

        assertEquals("io.micronaut.context.Qualifier", inject.members().get("Qualifier").binaryName());
    }

    @Test
    void preferPackageDecidesBetweenAnnotations() {
        ResolvedModule data = resolve(mappings(data()), "pyronaut.data");

        assertEquals("jakarta.persistence.Id", data.members().get("Id").binaryName());
        assertEquals("io.micronaut.data.annotation.MappedEntity", data.members().get("MappedEntity").binaryName());
        assertEquals("preferPackage jakarta.persistence", data.clashes().getFirst().reason());
    }

    @Test
    void annotationsWinBeforePreferredPackages() {
        ResolvedModule data = resolve(mappings(PythonModuleMapping.builder("pyronaut.data")
            .javaPackage("jakarta.persistence")
            .javaPackage("io.micronaut.data.annotation")
            .preferPackage("jakarta.persistence")
            .clashPolicy(ClashPolicy.PREFER_ANNOTATIONS)
            .build()), "pyronaut.data");

        // both are annotations: the preferred package decides
        assertEquals("jakarta.persistence.Id", data.members().get("Id").binaryName());
        // only Micronaut Data's Query is an annotation: it wins over the preferred package's interface
        assertEquals("io.micronaut.data.annotation.Query", data.members().get("Query").binaryName());
    }

    @Test
    void staticFieldsAreConstants() {
        ResolvedModule http = resolve(mappings(PythonModuleMapping.builder("pyronaut.http")
            .javaPackage("io.micronaut.http")
            .constants("io.micronaut.http.MediaType", "APPLICATION_JSON")
            .build()), "pyronaut.http");

        assertEquals(new Member("APPLICATION_JSON", Kind.CONSTANT, "io.micronaut.http.MediaType", "APPLICATION_JSON"), http.members().get("APPLICATION_JSON"));
        assertFalse(http.members().containsKey("TEXT_PLAIN"));
    }

    @Test
    void preferDecidesOneName() {
        ResolvedModule data = resolve(mappings(PythonModuleMapping.builder("pyronaut.data")
            .javaPackage("jakarta.persistence")
            .javaPackage("io.micronaut.data.annotation")
            .prefer("Id", "io.micronaut.data.annotation.Id")
            .prefer("Query", "io.micronaut.data.annotation.Query")
            .clashPolicy(ClashPolicy.FAIL)
            .build()), "pyronaut.data");

        assertEquals("io.micronaut.data.annotation.Id", data.members().get("Id").binaryName());
    }

    @Test
    void failPolicyReportsUndecidedClashes() {
        PythonImportMappings mappings = mappings(PythonModuleMapping.builder("pyronaut.data")
            .javaPackage("jakarta.persistence")
            .javaPackage("io.micronaut.data.annotation")
            .clashPolicy(ClashPolicy.FAIL)
            .build());

        PythonImportMappings.Resolver resolver = mappings.resolver(index);
        PythonImportMappingException e = assertThrows(PythonImportMappingException.class, () -> resolver.resolve("pyronaut.data"));
        assertTrue(e.getMessage().contains("[Id]"), e.getMessage());
        assertTrue(e.getMessage().contains("jakarta.persistence.Id"), e.getMessage());
    }

    @Test
    void preferOfAnUnknownTargetIsAnError() {
        PythonImportMappings mappings = mappings(PythonModuleMapping.builder("pyronaut.data")
            .javaPackage("jakarta.persistence")
            .javaPackage("io.micronaut.data.annotation")
            .prefer("Id", "com.example.Id")
            .build());

        PythonImportMappings.Resolver resolver = mappings.resolver(index);
        assertThrows(PythonImportMappingException.class, () -> resolver.resolve("pyronaut.data"));
    }

    @Test
    void kindsFilterThePackage() {
        ResolvedModule http = resolve(mappings(PythonModuleMapping.builder("pyronaut.http")
            .javaPackage("io.micronaut.http", TypeKind.ENUM, TypeKind.INTERFACE)
            .build()), "pyronaut.http");

        assertEquals(List.of("HttpMethod", "HttpResponse", "HttpStatus"), List.copyOf(http.members().keySet()));
    }

    @Test
    void excludeLeavesNamesOut() {
        ResolvedModule http = resolve(mappings(PythonModuleMapping.builder("pyronaut.http")
            .javaPackage("io.micronaut.http.annotation")
            .exclude("Controller")
            .build()), "pyronaut.http");

        assertEquals(List.of("Get"), List.copyOf(http.members().keySet()));
    }

    @Test
    void moduleIsInactiveWithoutItsFirstSource() {
        PythonImportMappings mappings = mappings(PythonModuleMapping.builder("pyronaut.sql")
            .javaPackage("io.micronaut.sql.annotation")
            .javaPackage("io.micronaut.http.annotation")
            .requiredArtifact("io.micronaut.sql:micronaut-jdbc")
            .build());

        ResolvedModule sql = resolve(mappings, "pyronaut.sql");
        assertFalse(sql.active());
        assertTrue(sql.members().isEmpty());
        assertTrue(sql.inactiveReason().contains("io.micronaut.sql.annotation"), sql.inactiveReason());
        assertTrue(sql.inactiveReason().contains("io.micronaut.sql:micronaut-jdbc"), sql.inactiveReason());
        assertTrue(mappings.resolver(index).resolve("pyronaut.unknown").isEmpty());
    }

    @Test
    void nestedModulesAreMembersWhenActive() {
        PythonImportMappings mappings = mappings(http(), PythonModuleMapping.builder("pyronaut.http.client")
            .javaPackage("io.micronaut.http.client.annotation")
            .build(), PythonModuleMapping.builder("pyronaut.http.missing")
            .javaPackage("io.micronaut.missing")
            .build());

        ResolvedModule http = resolve(mappings, "pyronaut.http");
        assertEquals(new Member("client", Kind.MODULE, "pyronaut.http.client", null), http.members().get("client"));
        assertFalse(http.members().containsKey("missing"));
        assertTrue(mappings.isNamespace("pyronaut"));
        assertTrue(mappings.isNamespace("pyronaut.http"));
        assertFalse(mappings.isNamespace("pyronaut.http.client"));
        assertTrue(mappings.isModule("pyronaut.http.client"));
    }

    @Test
    void contributionsMergeInOrder() {
        PythonImportMappings mappings = PythonImportMappings.of(List.of(
            mapper(10, PythonModuleMapping.builder("pyronaut.http").javaPackage("io.micronaut.http").build()),
            mapper(0, PythonModuleMapping.builder("pyronaut.http").documentation("first").javaPackage("io.micronaut.http.annotation").build())
        ));

        ResolvedModule http = resolve(mappings, "pyronaut.http");
        assertEquals("first", http.documentation());
        assertTrue(http.members().containsKey("Get"));
        assertTrue(http.members().containsKey("HttpResponse"));
    }

    @Test
    void replacingContributionDropsThoseOfLowerPrecedence() {
        PythonImportMappings mappings = PythonImportMappings.of(List.of(
            mapper(10, PythonModuleMapping.builder("pyronaut.http").javaPackage("io.micronaut.http").build()),
            mapper(0, PythonModuleMapping.builder("pyronaut.http").javaPackage("io.micronaut.http.annotation").replaces().build())
        ));

        ResolvedModule http = resolve(mappings, "pyronaut.http");
        assertTrue(http.members().containsKey("Get"));
        assertFalse(http.members().containsKey("HttpResponse"));
    }

    @Test
    void disagreeingContributionsFail() {
        PythonImportMappings mappings = PythonImportMappings.of(List.of(
            mapper(0, PythonModuleMapping.builder("pyronaut.data").javaPackage("jakarta.persistence").build()),
            mapper(1, PythonModuleMapping.builder("pyronaut.data").javaPackage("io.micronaut.data.annotation").build())
        ));

        PythonImportMappings.Resolver resolver = mappings.resolver(index);
        assertThrows(PythonImportMappingException.class, () -> resolver.resolve("pyronaut.data"));
    }

    @Test
    void preferBetweenContributionsNamesTheWinner() {
        PythonModuleMapping micronautId = PythonModuleMapping.builder("pyronaut.data").javaType("io.micronaut.data.annotation.Id").build();
        PythonModuleMapping jakartaId = PythonModuleMapping.builder("pyronaut.data").javaType("jakarta.persistence.Id").build();

        // the contribution of higher precedence prefers the type of the other one
        ResolvedModule higherPrefersOther = resolve(PythonImportMappings.of(List.of(
            mapper(0, PythonModuleMapping.builder("pyronaut.data").javaType("io.micronaut.data.annotation.Id")
                .prefer("Id", "jakarta.persistence.Id").build()),
            mapper(10, jakartaId))), "pyronaut.data");
        assertEquals("jakarta.persistence.Id", higherPrefersOther.members().get("Id").binaryName());

        // the contribution of lower precedence prefers the type of the higher one
        ResolvedModule lowerPrefersOther = resolve(PythonImportMappings.of(List.of(
            mapper(0, micronautId),
            mapper(10, PythonModuleMapping.builder("pyronaut.data").javaType("jakarta.persistence.Id")
                .prefer("Id", "io.micronaut.data.annotation.Id").build()))), "pyronaut.data");
        assertEquals("io.micronaut.data.annotation.Id", lowerPrefersOther.members().get("Id").binaryName());

        // a prefer naming neither candidate is an error
        PythonImportMappings neither = PythonImportMappings.of(List.of(
            mapper(0, PythonModuleMapping.builder("pyronaut.data").javaType("io.micronaut.data.annotation.Id")
                .prefer("Id", "com.example.Id").build()),
            mapper(10, jakartaId)));
        PythonImportMappings.Resolver resolver = neither.resolver(index);
        PythonImportMappingException e = assertThrows(PythonImportMappingException.class, () -> resolver.resolve("pyronaut.data"));
        assertTrue(e.getMessage().contains("com.example.Id"), e.getMessage());
    }

    @Test
    void fingerprintDoesNotDependOnDeclarationOrder() {
        PythonModuleMapping first = PythonModuleMapping.builder("pyronaut.http")
            .javaPackage("io.micronaut.http", TypeKind.ANNOTATION, TypeKind.ENUM, TypeKind.CLASS)
            .build();
        PythonModuleMapping second = PythonModuleMapping.builder("pyronaut.http")
            .javaPackage("io.micronaut.http", TypeKind.CLASS, TypeKind.ANNOTATION, TypeKind.ENUM)
            .build();

        assertEquals(mappings(first).fingerprint(), mappings(second).fingerprint());
        assertEquals("[ANNOTATION, ENUM, CLASS]", ((PythonModuleMapping.Source.JavaPackage) first.sources().getFirst()).kinds().toString());
    }

    @Test
    void includesKeepTheirOrderAndAllowRepeats() {
        PythonModuleMapping mapping = PythonModuleMapping.builder("pyronaut.http")
            .staticMethods("io.micronaut.http.HttpResponse", "ok", "notFound", "ok")
            .build();

        assertEquals("[ok, notFound]", ((PythonModuleMapping.Source.StaticMethods) mapping.sources().getFirst()).include().toString());
    }

    @Test
    void excludeAppliesToNestedModules() {
        ResolvedModule http = resolve(mappings(PythonModuleMapping.builder("pyronaut.http")
            .javaPackage("io.micronaut.http.annotation")
            .exclude("client")
            .build(), PythonModuleMapping.builder("pyronaut.http.client")
            .javaPackage("io.micronaut.http.client.annotation")
            .build()), "pyronaut.http");

        assertFalse(http.members().containsKey("client"));
    }

    @Test
    void aliasesAreValidatedAndKeywordSafe() {
        assertThrows(IllegalArgumentException.class, () -> new PythonModuleMapping.Source.JavaType("io.micronaut.http.HttpResponse", "bad-name"));
        ResolvedModule http = resolve(mappings(PythonModuleMapping.builder("pyronaut.http")
            .javaType("io.micronaut.http.HttpResponse", "import")
            .build()), "pyronaut.http");

        assertEquals("io.micronaut.http.HttpResponse", http.members().get("import_").binaryName());
    }

    @Test
    void keywordNamesGainUnderscore() {
        index.statics("io.micronaut.http.HttpResponse", "import");
        ResolvedModule http = resolve(mappings(PythonModuleMapping.builder("pyronaut.http")
            .staticMethods("io.micronaut.http.HttpResponse", "import")
            .build()), "pyronaut.http");

        assertEquals("import", http.members().get("import_").memberName());
    }

    @Test
    void invalidModuleNamesAreRejected() {
        PythonModuleMapping.Builder notDotted = PythonModuleMapping.builder("http").javaPackage("a");
        PythonModuleMapping.Builder notIdentifier = PythonModuleMapping.builder("pyronaut.http-x").javaPackage("a");
        PythonModuleMapping.Builder emptySegment = PythonModuleMapping.builder("pyronaut..http").javaPackage("a");
        PythonModuleMapping.Builder noSources = PythonModuleMapping.builder("pyronaut.http");
        assertThrows(IllegalArgumentException.class, notDotted::build);
        assertThrows(IllegalArgumentException.class, notIdentifier::build);
        assertThrows(IllegalArgumentException.class, emptySegment::build);
        assertThrows(IllegalArgumentException.class, noSources::build);
    }

    @Test
    void fingerprintFollowsTheMappings() {
        assertEquals(mappings(http()).fingerprint(), mappings(http()).fingerprint());
        assertNotEquals(mappings(http()).fingerprint(), mappings(data()).fingerprint());
    }

    private ResolvedModule resolve(PythonImportMappings mappings, String module) {
        return mappings.resolver(index).resolve(module).orElseThrow();
    }

    private static PythonModuleMapping http() {
        return PythonModuleMapping.builder("pyronaut.http")
            .documentation("HTTP.")
            .javaPackage("io.micronaut.http.annotation")
            .javaPackage("io.micronaut.http")
            .staticMethods("io.micronaut.http.HttpResponse")
            .constants("io.micronaut.http.HttpStatus")
            .build();
    }

    private static PythonModuleMapping data() {
        return PythonModuleMapping.builder("pyronaut.data")
            .javaPackage("jakarta.persistence")
            .javaPackage("io.micronaut.data.annotation")
            .preferPackage("jakarta.persistence")
            .clashPolicy(ClashPolicy.FAIL)
            .build();
    }

    private static PythonImportMappings mappings(PythonModuleMapping... mappings) {
        return PythonImportMappings.of(List.of(mapper(0, mappings)));
    }

    private static PythonImportMapper mapper(int order, PythonModuleMapping... mappings) {
        return new PythonImportMapper() {
            @Override
            public List<PythonModuleMapping> getMappings() {
                return List.of(mappings);
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    private static final class FakeIndex implements ClassIndex {
        private final Map<String, TypeInfo> types = new LinkedHashMap<>();
        private final Map<String, List<String>> statics = new LinkedHashMap<>();
        private final Map<String, List<String>> constants = new LinkedHashMap<>();

        FakeIndex annotation(String name) {
            return type(name, TypeKind.ANNOTATION);
        }

        FakeIndex type(String name, TypeKind kind) {
            return type(name, kind, true, false);
        }

        FakeIndex type(String name, TypeKind kind, boolean publicType, boolean internal) {
            types.put(name, new TypeInfo(name, kind, publicType, internal));
            return this;
        }

        FakeIndex statics(String type, String... names) {
            statics.computeIfAbsent(type, k -> new ArrayList<>()).addAll(List.of(names));
            return this;
        }

        FakeIndex constants(String type, String... names) {
            constants.computeIfAbsent(type, k -> new ArrayList<>()).addAll(List.of(names));
            return this;
        }

        @Override
        public List<TypeInfo> types(String javaPackage) {
            return types.values().stream().filter(t -> t.packageName().equals(javaPackage)).toList();
        }

        @Override
        public Optional<TypeInfo> type(String binaryName) {
            return Optional.ofNullable(types.get(binaryName));
        }

        @Override
        public List<String> staticMethods(String binaryName) {
            return statics.getOrDefault(binaryName, List.of());
        }

        @Override
        public List<String> constants(String binaryName) {
            return constants.getOrDefault(binaryName, List.of());
        }
    }
}
