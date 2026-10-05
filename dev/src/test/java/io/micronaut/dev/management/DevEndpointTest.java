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

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevEndpointTest {

    private static final String OWN = "http://localhost:8080";

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
