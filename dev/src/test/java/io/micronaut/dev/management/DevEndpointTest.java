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
package io.micronaut.dev.management;

import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.DevRuntimes;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.compile.SourceKind;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevEndpointTest {

    private static final String OWN = "http://localhost:8080";

    @TempDir
    Path project;

    @Test
    void theStatusOfARuntimeThatDidNotStart() throws Exception {
        try (DevRuntime runtime = DevRuntimes.unstarted(project)) {
            Map<String, Object> status = new DevEndpoint(runtime, request -> OWN).status();
            assertEquals(runtime.generation(), status.get(DevEndpoint.KEY_GENERATION));
            assertEquals(runtime.strategy().name(), status.get(DevEndpoint.KEY_STRATEGY));
            assertEquals(false, status.get(DevEndpoint.KEY_RELOADING));
            assertEquals(0, status.get(DevEndpoint.KEY_RETAINED));
            assertEquals(List.of(), status.get(DevEndpoint.KEY_WATCHED));
            assertTrue(status.containsKey(DevEndpoint.KEY_LIVERELOAD));
            assertNull(status.get(DevEndpoint.KEY_LIVERELOAD));
            assertNull(status.get(DevEndpoint.KEY_FAILURE));
        }
    }

    @Test
    void theEndpointReloadsOnlyForTheReloadActionOfAnAcceptedRequest() throws Exception {
        try (DevRuntime runtime = DevRuntimes.unstarted(project)) {
            DevEndpoint endpoint = new DevEndpoint(runtime, request -> OWN);

            HttpResponse<Map<String, Object>> refused = endpoint.act(DevEndpoint.ACTION_RELOAD, reload().header(HttpHeaders.ORIGIN, "https://evil.example"));
            assertEquals(HttpStatus.FORBIDDEN, refused.getStatus());
            assertEquals(false, refused.body().get(DevEndpoint.KEY_ACCEPTED));
            assertNotNull(refused.body().get(DevEndpoint.KEY_REASON));

            HttpResponse<Map<String, Object>> unknown = endpoint.act("explode", reload());
            assertEquals(HttpStatus.OK, unknown.getStatus());
            assertEquals(Map.of(DevEndpoint.KEY_ACCEPTED, false, DevEndpoint.KEY_ACTION, "explode"), unknown.body());

            HttpResponse<Map<String, Object>> accepted = endpoint.act(DevEndpoint.ACTION_RELOAD, reload());
            assertEquals(HttpStatus.OK, accepted.getStatus());
            assertEquals(Map.of(DevEndpoint.KEY_ACCEPTED, true, DevEndpoint.KEY_ACTION, DevEndpoint.ACTION_RELOAD), accepted.body());
        }
    }

    @Test
    void aFailureIsDescribedByItsErrorsAndHowManyWarningsItHad() {
        Instant at = Instant.parse("2026-10-11T00:00:00Z");
        CompileFailure failure = new CompileFailure(SourceKind.JAVA, List.of(
            new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "cannot find symbol", Path.of("src/main/java/app/A.java"), 3, 7),
            new CompileDiagnostic(CompileDiagnostic.Severity.ERROR, "no source", null, 0, 0),
            new CompileDiagnostic(CompileDiagnostic.Severity.WARNING, "deprecated", null, 0, 0)), at);
        Map<String, Object> description = DevEndpoint.describe(failure);
        assertEquals("java", description.get("kind"));
        assertEquals(at.toString(), description.get("at"));
        assertEquals(List.of(Path.of("src/main/java/app/A.java") + ":3: cannot find symbol", "no source"), description.get("errors"));
        assertEquals(1L, description.get("warnings"));
        assertFalse(description.containsKey("diagnostics"));
    }

    @Test
    void theEndpointIsSensitiveUnlessConfiguredOtherwise() throws Exception {
        // it reports absolute paths and its reload compiles and restarts the application. Read from the class file: the
        // management module stays off the test runtime classpath
        byte[] bytes;
        try (InputStream in = DevEndpoint.class.getResourceAsStream("DevEndpoint.class")) {
            bytes = in.readAllBytes();
        }
        Annotation endpoint = ClassFile.of().parse(bytes).findAttribute(Attributes.runtimeVisibleAnnotations()).orElseThrow().annotations().stream()
            .filter(annotation -> annotation.classSymbol().descriptorString().equals("Lio/micronaut/management/endpoint/annotation/Endpoint;"))
            .findFirst()
            .orElseThrow();
        // left out, the attribute takes its default, which is sensitive
        boolean sensitive = endpoint.elements().stream()
            .filter(element -> element.name().stringValue().equals("defaultSensitive"))
            .map(element -> ((AnnotationValue.OfBoolean) element.value()).booleanValue())
            .findFirst()
            .orElse(true);
        assertTrue(sensitive);
    }

    @Test
    void aClientThatIsNoPageReloadsWithAJsonRequest() {
        assertNull(DevEndpoint.refusal(reload(), OWN));
    }

    @Test
    void aPageOfTheServersOwnOriginReloads() {
        assertNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "http://localhost:8080").header(DevEndpoint.HEADER_FETCH_SITE, "same-origin"), OWN));
        // a default port written or left out
        assertNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "http://localhost"), "http://localhost:80"));
        assertNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "http://localhost:80"), "http://localhost"));
        assertNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "http://[::1]:8080"), "http://[::1]:8080"));
        // behind a proxy terminating TLS, the origin the host resolver gives from the forwarding headers
        assertNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "https://workspace.example"), "https://workspace.example"));
    }

    @Test
    void aFormOrASimpleRequestCannotReload() {
        // what a page of any site can send without a preflight
        assertNotNull(DevEndpoint.refusal(HttpRequest.POST("/dev/reload", ""), OWN));
        assertNotNull(DevEndpoint.refusal(HttpRequest.POST("/dev/reload", "").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), OWN));
        assertNotNull(DevEndpoint.refusal(HttpRequest.POST("/dev/reload", "").contentType(MediaType.TEXT_PLAIN_TYPE), OWN));
    }

    @Test
    void aRequestFromAnotherOriginCannotReload() {
        assertNotNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "https://evil.example"), OWN));
        assertNotNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "http://localhost:9090"), OWN));
        assertNotNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "https://localhost:8080"), OWN));
        assertNotNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "null"), OWN));
        assertNotNull(DevEndpoint.refusal(reload().header(DevEndpoint.HEADER_FETCH_SITE, "cross-site"), OWN));
        assertNotNull(DevEndpoint.refusal(reload().header(DevEndpoint.HEADER_FETCH_SITE, "same-site"), OWN));
        // another host on the same port
        assertNotNull(DevEndpoint.refusal(reload().header(HttpHeaders.ORIGIN, "http://other.example:8080"), OWN));
    }

    private static MutableHttpRequest<?> reload() {
        return HttpRequest.POST("/dev/reload", "{}").contentType(MediaType.APPLICATION_JSON_TYPE);
    }
}
