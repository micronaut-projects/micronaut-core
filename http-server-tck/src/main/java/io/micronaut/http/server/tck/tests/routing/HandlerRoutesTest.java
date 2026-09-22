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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.web.router.RouteInfo;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.RouteSource;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteSpec;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routes to handler functions, declared by {@link HttpRoutes} beans or published at runtime by a
 * {@link RouteSource}, run like controller routes: filters and error routes apply, bodies are
 * decoded and encoded, {@code GET} routes answer {@code HEAD}, and a wrong method is answered
 * with 405.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRoutesTest {
    public static final String SPEC_NAME = "HandlerRoutesTest";
    private static final String TRACE = "handler-routes-trace";
    private static final String FILTER_THREAD = "handler-routes-filter-thread";

    @Test
    void handlerRouteIsHandledAndFiltered() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Hello Fred")
                .headers(Map.of("X-Fn-Filter", "true"))
                .build());
        }
    }

    @Test
    void headRequestIsHandledByTheGetRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
        }
    }

    @Test
    void bodyIsDecodedAndTheResultEncoded() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/items", Map.of("name", "apple")), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("{\"saved\":\"apple\"}")
                .build());
        }
    }

    @Test
    void asyncBodyHandlerDecodesTheBodyAndCompletesLater() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/async-items", Map.of("name", "apple")), HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("{\"saved\":\"apple later\"}")
                .build());
        }
    }

    @Test
    void handlerRouteHasTheAnnotationsOfTheMethodItImplements() throws IOException {
        try (ServerUnderTest server = server()) {
            // a filter that reads the annotations of the matched route, and of its return type, sees them
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/annotated"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("annotated")
                .headers(Map.of("X-Marked", "from the bean method", "X-Return-Marked", "from the bean method"))
                .build());
        }
    }

    @Test
    void nullableBodyIsNullWithoutABody() throws IOException {
        try (ServerUnderTest server = server()) {
            for (String path : List.of("/fn/nullable-body", "/fn/nullable-body-async")) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST(path, "text").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("body text")
                    .build());
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST(path, null).contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("no body")
                    .build());
            }
        }
    }

    @Test
    void asyncHandlerCompletesTheResponse() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/async"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async")
                .build());
        }
    }

    @Test
    void errorRouteHandlesTheHandlerException() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/fail"), HttpResponseAssertion.builder()
                .status(HttpStatus.CONFLICT)
                .body("handled checked failure")
                .build());
        }
    }

    @Test
    void wrongMethodIsNotAllowed() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void routeRequestFilterAnswersInsteadOfTheRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/guarded"), HttpResponseAssertion.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .headers(Map.of("X-Fn-Filter", "true"))
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.HEAD("/fn/guarded"), HttpResponseAssertion.builder()
                .status(HttpStatus.UNAUTHORIZED)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/guarded").header("X-Token", "secret"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("guarded")
                .build());
        }
    }

    @Test
    void routeResponseFiltersAlsoFilterTheAnswerOfARouteRequestFilter() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/rejected"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .headers(Map.of("X-After", "after1,after2"))
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/rejected").header("X-Token", "secret"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("accepted")
                .headers(Map.of("X-After", "after1,after2"))
                .build());
        }
    }

    @Test
    void routeFiltersRunClosestToTheRouteInTheOrderDeclared() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/trace"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("global,before1,before2")
                .headers(Map.of("X-Trace", "after1,after2,global"))
                .build());
        }
    }

    @Test
    void asyncRouteFiltersCompleteTheChainLater() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn/async-guarded"), HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/async-guarded").header("X-Token", "secret"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async guarded")
                .headers(Map.of("X-Async-After", "true"))
                .build());
        }
    }

    @Test
    void routeFilterRunsOnItsExecutor() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/filter-executor"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("handler-filter-thread")
                .build());
        }
    }

    @Test
    void urlEncodedFormIsReadIntoFormData() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms/7", "name=Fred&age=42&tag=a&tag=b")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("7 Fred 43 [a, b] no-file")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void multipartFormIsReadIntoFormData() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("age", "42")
                .addPart("tag", "a")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .build();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms/8", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("8 Fred 43 [a] avatar.txt=picture")
                .build());
        }
    }

    @Test
    void missingFormFieldCanHaveADefaultValue() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-defaults", "quantity=3")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("3 standard false")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-defaults", "shipping=express&gift=true")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("1 express true")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms-defaults", "quantity=many")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
        }
    }

    @Test
    void missingOrInvalidFormFieldIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms/7", "age=42")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms/7", "name=Fred&age=old")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
        }
    }

    @Test
    void asyncFormHandlerCompletesLater() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-async", "name=Fred")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async Fred")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void streamingFormHandlerReadsPartsAsTheyArrive() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("ignored", "not read")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .addPart("age", "42")
                .build();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-stream", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("name=Fred;avatar.txt=picture;age=42;")
                .build());
        }
    }

    @Test
    void streamingFormHandlerReadsOneFieldAndDiscardsTheRest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-part/age", Map.of("name", "Fred", "age", "42", "city", "Prague"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("age=42")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms-part/missing", Map.of("name", "Fred"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .body("no part missing")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void streamingFormHandlerReadsOneFileAndDiscardsTheRest() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("document", "cv.txt", MediaType.TEXT_PLAIN_TYPE, "not read".getBytes(StandardCharsets.UTF_8))
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .addPart("avatar", "second.txt", MediaType.TEXT_PLAIN_TYPE, "second".getBytes(StandardCharsets.UTF_8))
                .addPart("age", "42")
                .build();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-part/avatar", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("avatar.txt=picture")
                .build());
        }
    }

    @Test
    void streamingFormHandlerReadsSeveralPartsInOrder() throws IOException {
        try (ServerUnderTest server = server()) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("name", "Fred");
            form.put("age", "42");
            form.put("city", "Prague");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-cursor/name/city", form)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("name=Fred;city=Prague;")
                .build());
            // the cursor only moves forward: a part sent before the current position is not found
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-cursor/city/name", form)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("city=Prague;no name;")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void streamingFormHandlerClosesThePartsToThrowOutTheRest() throws IOException {
        try (ServerUnderTest server = server()) {
            byte[] rest = new byte[256 * 1024];
            Arrays.fill(rest, (byte) 'x');
            for (int i = 0; i < 2; i++) {
                // twice: the connection stays usable after the rest of the body was thrown out
                MultipartBody body = MultipartBody.builder()
                    .addPart("name", "Fred")
                    .addPart("document", "cv.txt", MediaType.TEXT_PLAIN_TYPE, "not read".getBytes(StandardCharsets.UTF_8))
                    .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                    .addPart("archive", "archive.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE, rest)
                    .addPart("age", "42")
                    .build();
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-cursor/name/avatar", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("name=Fred;avatar.txt=picture;")
                    .build());
            }
        }
    }

    @Test
    @Tag("multipart")
    void aStreamObtainedButNotReadIsDiscarded() throws IOException {
        try (ServerUnderTest server = server()) {
            for (int i = 0; i < 2; i++) {
                // twice: the form continues past the abandoned part, and the connection stays usable
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-abandoned", largeForm()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("name=Fred;age=42;")
                    .build());
            }
        }
    }

    @Test
    @Tag("multipart")
    void aConsumerFailingAfterObtainingAStreamIsAnsweredLikeAnyError() throws IOException {
        try (ServerUnderTest server = server()) {
            for (int i = 0; i < 2; i++) {
                AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms-failing", largeForm()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build());
            }
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/hello/Fred"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Hello Fred")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void closingThePartsWhileAConsumerHoldsAPartDiscardsIt() throws IOException {
        try (ServerUnderTest server = server()) {
            for (int i = 0; i < 2; i++) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-close-pending", largeForm()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("closed while pending")
                    .build());
            }
        }
    }

    @Test
    void annotatedHandlersBindTheFormContracts() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/annotated-forms/data", "name=Fred")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("data Fred 1")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/annotated-forms/data", "age=3")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/annotated-forms/parts", "age=3&name=Fred&city=Prague")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("parts [Fred]")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void collectedFileIsTransferredToANewFile() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .build();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-transfer", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Fred avatar.txt 7 picture")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void transferDoesNotReplaceAnExistingFile() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .build();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-existing", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("exists kept")
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void fileLargerThanTheLimitIsRejected() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .build();
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms-limited", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void closingTheFormReleasesTheFilesAndKeepsTheText() throws IOException {
        try (ServerUnderTest server = server()) {
            MultipartBody body = MultipartBody.builder()
                .addPart("name", "Fred")
                .addPart("avatar", "avatar.txt", MediaType.TEXT_PLAIN_TYPE, "picture".getBytes(StandardCharsets.UTF_8))
                .build();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-closed", body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Fred avatar.txt closed")
                .build());
        }
    }

    @Test
    void requiredFileThatIsMissingIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms-transfer", "name=Fred")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
        }
    }

    @Test
    void textPartAskedForAsAFileIsABadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms-part-file/name", "name=Fred")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
        }
    }

    @Test
    void boundedTextIsReadAndTheRestReleased() throws IOException {
        try (ServerUnderTest server = server()) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("title", "Report");
            form.put("rest", "not read");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-bounded/8", form)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("[Report]")
                .build());
            // the limit counts bytes
            AssertionUtils.assertThrows(server, HttpRequest.POST("/fn/forms-bounded/5", form)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.REQUEST_ENTITY_TOO_LARGE)
                .build());
        }
    }

    @Test
    @Tag("multipart")
    void takenBodyIsConsumedBeforeTheNextPart() throws IOException {
        try (ServerUnderTest server = server()) {
            for (int i = 0; i < 2; i++) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-taken", largeForm()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("name=4;archive=262144;age=2;")
                    .build());
            }
        }
    }

    @Test
    @Tag("multipart")
    void closingThePartsCancelsTheOperationInProgress() throws IOException {
        try (ServerUnderTest server = server()) {
            for (int i = 0; i < 2; i++) {
                AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/forms-close-cancels", largeForm()).contentType(MediaType.MULTIPART_FORM_DATA_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("cancelled, then refused")
                    .build());
            }
        }
    }

    private static MultipartBody largeForm() {
        byte[] archive = new byte[256 * 1024];
        Arrays.fill(archive, (byte) 'x');
        return MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("archive", "archive.bin", MediaType.APPLICATION_OCTET_STREAM_TYPE, archive)
            .addPart("age", "42")
            .build();
    }

    @Test
    void handlerIsBoundToADeclaredRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/declared/5"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("declared 5 handler-filter-thread")
                .headers(Map.of("X-Declared", "true", "X-Fn-Filter", "true"))
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/fn/declared/5"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .headers(Map.of("X-Declared", "true"))
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/fn/declared/5"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void routeConfigurationIsFixedWhenTheRouterIsBuilt() throws IOException {
        try (ServerUnderTest server = server()) {
            FrozenRoutes routes = server.getApplicationContext().getBean(FrozenRoutes.class);
            // changed after the router took the routes, before the first request: ignored
            routes.consumes[0] = MediaType.APPLICATION_XML_TYPE;
            routes.produces[0] = MediaType.IMAGE_PNG_TYPE;
            routes.eagerThread.executeOn("handler-filter");
            routes.declaredThread.executeOn("handler-filter");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/fn/frozen/declared", "fred")
                .contentType(MediaType.TEXT_PLAIN_TYPE)
                .accept(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("saved fred")
                .build());
            for (String path : List.of("/fn/frozen/eager-thread", "/fn/frozen/declared-thread")) {
                String thread = server.exchange(HttpRequest.GET(path), String.class).body();
                assertNotEquals("handler-filter-thread", thread, path);
            }
        }
    }

    @Test
    void declaredRouteIsFixedWhenTheRouterIsBuilt() throws IOException {
        try (ServerUnderTest server = server()) {
            Router router = server.getApplicationContext().getBean(Router.class);
            assertTrue(router.uriRoutes().anyMatch(route -> route.toString().startsWith("GET /fn/declared/{id}")));
            // changed after the router took the route: ignored
            server.getApplicationContext().getBean(DeclaredRoutes.class).route.consumes(MediaType.TEXT_XML_TYPE).before(request -> HttpResponse.serverError());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/declared/6"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
        }
    }

    @Test
    void runtimeTableBindsADeclaredRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            server.getApplicationContext().getBean(DynamicHandlerRoutes.class).enable();

            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn-declared-dynamic/7"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("dynamic declared 7")
                .headers(Map.of("X-Table-Route", "true"))
                .build());
        }
    }

    @Test
    void asyncGetRoute() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn/async-get"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("async get")
                .build());
        }
    }

    @Test
    void handlerIsBoundToSeveralMethods() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.PUT("/fn/multi", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("multi PUT")
                .headers(Map.of("X-Multi", "true"))
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.PATCH("/fn/multi", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("multi PATCH")
                .headers(Map.of("X-Multi", "true"))
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.DELETE("/fn/multi"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
        }
    }

    @Test
    void runtimeRoutesUseHandlers() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, HttpRequest.GET("/fn-dynamic/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());

            server.getApplicationContext().getBean(DynamicHandlerRoutes.class).enable();

            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/fn-dynamic/x"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("dynamic /fn-dynamic/x")
                .headers(Map.of("X-Fn-Filter", "true", "X-Table-Route", "true"))
                .build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    /**
     * Completes on an executor of the application, like a service call would: the filter chain
     * then continues on that thread.
     */
    private static <T> CompletableFuture<T> completeLater(ExecutorService executor, Supplier<T> value) {
        return CompletableFuture.supplyAsync(value, executor);
    }

    /**
     * @return A new file in a new directory, which does not exist yet: uploads are written to new files
     */
    private static Path temporaryFile() {
        try {
            Path directory = Files.createTempDirectory("handler-routes");
            directory.toFile().deleteOnExit();
            Path file = directory.resolve("upload");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.writeString(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static @Nullable Throwable cause(@Nullable Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HttpResponse<?> append(HttpRequest<?> request, String step) {
        request.setAttribute(TRACE, request.getAttribute(TRACE, String.class).orElse("") + "," + step);
        return null;
    }

    static final class CheckedFailure extends Exception {
        CheckedFailure(String message) {
            super(message);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Routes {
        @Singleton
        @Named("fn")
        HttpRoutes fnRoutes(@Named("handler-filter") ExecutorService executor) {
            return routes -> {
                routes.GET("/fn/hello/{name}", (request, pathVariables) ->
                    HttpResponse.ok("Hello " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
                MutableAnnotationMetadata nullable = new MutableAnnotationMetadata();
                nullable.addDeclaredAnnotation(AnnotationUtil.NULLABLE, Map.of());
                routes.POST("/fn/nullable-body", Argument.of(String.class, "body", nullable), (request, pathVariables, body) ->
                    HttpResponse.ok(body == null ? "no body" : "body " + body).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .consumesAll();
                routes.asyncPOST("/fn/nullable-body-async", Argument.of(String.class, "body", nullable), (request, pathVariables, body) ->
                    completeLater(executor, () -> HttpResponse.ok(body == null ? "no body" : "body " + body).contentType(MediaType.TEXT_PLAIN_TYPE)))
                    .consumesAll();
                routes.POST("/fn/items", Argument.mapOf(String.class, String.class), (request, pathVariables, item) ->
                    HttpResponse.created(Map.of("saved", item.get("name"))));
                routes.asyncPOST("/fn/async-items", Argument.mapOf(String.class, String.class), (request, pathVariables, item) ->
                    completeLater(executor, () -> HttpResponse.created(Map.of("saved", item.get("name") + " later"))));
                routes.handleAsync(HttpMethod.GET, "/fn/async", (request, pathVariables) ->
                    completeLater(executor, () -> HttpResponse.ok("async").contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.GET("/fn/guarded", (request, pathVariables) -> HttpResponse.ok("guarded").contentType(MediaType.TEXT_PLAIN_TYPE))
                    .before(request -> "secret".equals(request.getHeaders().get("X-Token")) ? null : HttpResponse.unauthorized());
                routes.GET("/fn/trace", (request, pathVariables) -> HttpResponse.ok(request.getAttribute(TRACE, String.class).orElse("")).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .before(request -> append(request, "before1"))
                    .before(request -> append(request, "before2"))
                    .after((request, response) -> response.getHeaders().set("X-Trace", "after1"))
                    .after((request, response) -> response.getHeaders().set("X-Trace", response.getHeaders().get("X-Trace") + ",after2"));
                routes.GET("/fn/async-guarded", (request, pathVariables) -> HttpResponse.ok("async guarded").contentType(MediaType.TEXT_PLAIN_TYPE))
                    .beforeAsync(request -> completeLater(executor, () ->
                        "secret".equals(request.getHeaders().get("X-Token")) ? null : HttpResponse.status(HttpStatus.FORBIDDEN)))
                    .afterAsync((request, response) -> completeLater(executor, () -> response.header("X-Async-After", "true")));
                routes.GET("/fn/filter-executor", (request, pathVariables) ->
                        HttpResponse.ok(request.getAttribute(FILTER_THREAD, String.class).orElse("")).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .before("handler-filter", request -> {
                        request.setAttribute(FILTER_THREAD, Thread.currentThread().getName());
                        return null;
                    });
                routes.handleFormAsync(HttpMethod.POST, "/fn/forms/{id}", (request, pathVariables, form) -> {
                    String fields = pathVariables.getLong("id") + " " + form.getString("name") + " " + (form.getInt("age") + 1)
                        + " " + form.getValues("tag") + " ";
                    CompletionStage<String> file = form.findFile("avatar")
                        .map(upload -> upload.bytes(1024).thenApply(bytes -> upload.fileName() + "=" + new String(bytes, StandardCharsets.UTF_8)))
                        .orElse(CompletableFuture.completedFuture("no-file"));
                    return file.thenApply(value -> HttpResponse.ok(fields + value).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.POST("/fn/forms-defaults", (request, pathVariables, form) ->
                    HttpResponse.ok(form.getInt("quantity", 1) + " " + form.getString("shipping", "standard") + " " + form.getBoolean("gift", false))
                        .contentType(MediaType.TEXT_PLAIN_TYPE));
                routes.handleFormAsync(HttpMethod.POST, "/fn/forms-async", (request, pathVariables, form) ->
                    completeLater(executor, () -> HttpResponse.ok("async " + form.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-stream", (request, pathVariables, parts) -> {
                    StringBuilder result = new StringBuilder();
                    return parts.forEach(part -> {
                        if (part.name().equals("ignored")) {
                            // not read: discarded before the next part
                            return CompletableFuture.completedFuture(null);
                        }
                        if (part.isFile()) {
                            Path file = temporaryFile();
                            return part.transferTo(file).thenAccept(done ->
                                result.append(part.fileName()).append('=').append(read(file)).append(';'));
                        }
                        return part.text().thenAccept(value -> result.append(part.name()).append('=').append(value).append(';'));
                    }).thenApply(done -> HttpResponse.ok(result.toString()).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-part/{name}", (request, pathVariables, parts) -> {
                    StringBuilder result = new StringBuilder();
                    return parts.part(pathVariables.getString("name"), part -> {
                        if (part.isFile()) {
                            Path file = temporaryFile();
                            return part.transferTo(file).thenAccept(done -> result.append(part.fileName()).append('=').append(read(file)));
                        }
                        return part.text().thenAccept(value -> result.append(part.name()).append('=').append(value));
                    }).thenApply(found -> found
                        ? HttpResponse.ok(result.toString()).contentType(MediaType.TEXT_PLAIN_TYPE)
                        : HttpResponse.badRequest("no part " + pathVariables.getString("name")).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-cursor/{first}/{second}", (request, pathVariables, parts) -> {
                    StringBuilder result = new StringBuilder();
                    Function<FormPart, CompletionStage<?>> append = part -> {
                        if (part.isFile()) {
                            Path file = temporaryFile();
                            return part.transferTo(file).thenAccept(done -> result.append(part.fileName()).append('=').append(read(file)).append(';'));
                        }
                        return part.text().thenAccept(value -> result.append(part.name()).append('=').append(value).append(';'));
                    };
                    String first = pathVariables.getString("first");
                    String second = pathVariables.getString("second");
                    return parts.part(first, append)
                        .thenCompose(found -> {
                            if (!found) {
                                result.append("no ").append(first).append(';');
                            }
                            return parts.part(second, append);
                        })
                        .thenApply(found -> {
                            if (!found) {
                                result.append("no ").append(second).append(';');
                            }
                            parts.close();
                            return HttpResponse.ok(result.toString()).contentType(MediaType.TEXT_PLAIN_TYPE);
                        });
                });
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-abandoned", (request, pathVariables, parts) -> {
                    StringBuilder result = new StringBuilder();
                    return parts.forEach(part -> {
                        if (part.isFile()) {
                            // obtained, never read: discarded when the consumer's stage completes
                            part.file();
                            return CompletableFuture.completedFuture(null);
                        }
                        return part.text().thenAccept(value -> result.append(part.name()).append('=').append(value).append(';'));
                    }).thenApply(done -> HttpResponse.ok(result.toString()).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-failing", (request, pathVariables, parts) ->
                    parts.forEach(part -> {
                        if (part.isFile()) {
                            part.file();
                            throw new IllegalStateException("failed after obtaining the file");
                        }
                        return CompletableFuture.completedFuture(null);
                    }).thenApply(done -> HttpResponse.ok("not reached")));
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-close-pending", (request, pathVariables, parts) -> {
                    CompletableFuture<Void> holding = new CompletableFuture<>();
                    parts.part("archive", part -> {
                        // obtained and held, never read, and the consumer never completes
                        part.file();
                        holding.complete(null);
                        return new CompletableFuture<>();
                    });
                    return holding.thenApply(held -> {
                        parts.close();
                        return HttpResponse.ok("closed while pending").contentType(MediaType.TEXT_PLAIN_TYPE);
                    });
                });
                routes.handleFormAsync(HttpMethod.POST, "/fn/forms-transfer", (request, pathVariables, form) -> {
                    String name = form.getString("name");
                    FileUpload avatar = form.getFile("avatar");
                    Path destination = temporaryFile();
                    // the transfer is part of the stage returned: the request owns the upload until then
                    return avatar.transferTo(destination)
                        .thenApply(done -> HttpResponse.ok(name + " " + avatar.fileName() + " " + avatar.size().orElse(-1) + " " + read(destination))
                            .contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.handleFormAsync(HttpMethod.POST, "/fn/forms-existing", (request, pathVariables, form) -> {
                    Path destination = temporaryFile();
                    write(destination, "kept");
                    return form.getFile("avatar").transferTo(destination).handle((done, error) -> {
                        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                        String result = (cause instanceof FileAlreadyExistsException ? "exists" : "unexpected " + cause) + " " + read(destination);
                        return HttpResponse.ok(result).contentType(MediaType.TEXT_PLAIN_TYPE);
                    });
                });
                routes.handleFormAsync(HttpMethod.POST, "/fn/forms-limited", (request, pathVariables, form) ->
                    form.getFile("avatar").bytes(3).thenApply(bytes -> HttpResponse.ok("not reached")));
                routes.handleFormAsync(HttpMethod.POST, "/fn/forms-closed", (request, pathVariables, form) -> {
                    FileUpload avatar = form.getFile("avatar");
                    return form.closeAsync().thenApply(closed -> {
                        String state;
                        try {
                            avatar.bytes(100);
                            state = "open";
                        } catch (IllegalStateException e) {
                            state = "closed";
                        }
                        return HttpResponse.ok(form.getString("name") + " " + avatar.fileName() + " " + state).contentType(MediaType.TEXT_PLAIN_TYPE);
                    });
                });
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-part-file/{name}", (request, pathVariables, parts) ->
                    parts.part(pathVariables.getString("name"), part -> part.file().transferTo(temporaryFile()))
                        .thenApply(found -> HttpResponse.ok("not reached")));
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-bounded/{limit}", (request, pathVariables, parts) -> {
                    List<String> titles = new ArrayList<>();
                    return parts.part("title", part -> part.text(pathVariables.getInt("limit")).thenAccept(titles::add))
                        .thenCompose(found -> parts.closeAsync()
                            .thenApply(closed -> HttpResponse.ok(titles.toString()).contentType(MediaType.TEXT_PLAIN_TYPE)));
                });
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-taken", (request, pathVariables, parts) -> {
                    StringBuilder result = new StringBuilder();
                    return parts.forEach(part -> {
                        // the callback takes the body, and consumes it before its stage completes
                        CloseableByteBody body = part.takeBody();
                        return body.buffer().thenAccept(available -> {
                            try (available) {
                                result.append(part.name()).append('=').append(available.length()).append(';');
                            }
                        });
                    }).thenApply(done -> HttpResponse.ok(result.toString()).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.handleFormStream(HttpMethod.POST, "/fn/forms-close-cancels", (request, pathVariables, parts) -> {
                    CompletableFuture<Void> holding = new CompletableFuture<>();
                    CompletionStage<Boolean> operation = parts.part("archive", part -> {
                        holding.complete(null);
                        // never completes: closing the parts ends the operation
                        return new CompletableFuture<>();
                    });
                    return holding
                        .thenCompose(held -> parts.closeAsync())
                        .thenCompose(closed -> operation.handle((found, error) -> cause(error) instanceof CancellationException ? "cancelled" : "not cancelled " + error))
                        .thenCompose(first -> parts.forEach(part -> CompletableFuture.completedFuture(null)).handle((done, error) ->
                            first + ", then " + (cause(error) instanceof IllegalStateException ? "refused" : "not refused " + error)))
                        .thenApply(result -> HttpResponse.ok(result).contentType(MediaType.TEXT_PLAIN_TYPE));
                });
                routes.GET("/fn/rejected", (request, pathVariables) -> HttpResponse.ok("accepted").contentType(MediaType.TEXT_PLAIN_TYPE))
                    .before(request -> request.getHeaders().contains("X-Token") ? null : HttpResponse.status(HttpStatus.FORBIDDEN))
                    .after((request, response) -> response.header("X-After", "after1"))
                    .after((request, response) -> response.getHeaders().set("X-After", response.getHeaders().get("X-After") + ",after2"));
                routes.asyncGET("/fn/async-get", (request, pathVariables) ->
                    completeLater(executor, () -> HttpResponse.ok("async get").contentType(MediaType.TEXT_PLAIN_TYPE)));
                routes.handle(Set.of(HttpMethod.PUT, HttpMethod.PATCH), "/fn/multi", (request, pathVariables) ->
                        HttpResponse.ok("multi " + request.getMethodName()).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .consumesAll()
                    .after((request, response) -> response.header("X-Multi", "true"));
                routes.GET("/fn/fail", (request, pathVariables) -> {
                    throw new CheckedFailure("checked failure");
                });
            };
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Executors {
        @Singleton
        @Named("handler-filter")
        @Bean(preDestroy = "shutdown")
        ExecutorService handlerFilterExecutor() {
            return java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "handler-filter-thread"));
        }
    }

    /**
     * Keeps the routes and the arrays it configured them with, to change them after the router
     * took the routes.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FrozenRoutes implements HttpRoutes {
        static final RouteDeclaration SAVE = RouteDeclaration.of(HttpMethod.POST, "/fn/frozen/declared");
        static final RouteDeclaration THREAD = RouteDeclaration.of(HttpMethod.GET, "/fn/frozen/declared-thread");

        final MediaType[] consumes = {MediaType.TEXT_PLAIN_TYPE};
        final MediaType[] produces = {MediaType.TEXT_PLAIN_TYPE};
        HttpRouteSpec declared;
        HttpRouteSpec declaredThread;
        HttpRouteSpec eagerThread;

        @Override
        public void routes(HttpRouteBuilder routes) {
            declared = routes.handle(SAVE, Argument.of(String.class), (request, pathVariables, body) ->
                    HttpResponse.ok("saved " + body).contentType(MediaType.TEXT_PLAIN_TYPE))
                .consumes(consumes)
                .produces(produces);
            declaredThread = routes.handle(THREAD, (request, pathVariables) -> threadName());
            eagerThread = routes.GET("/fn/frozen/eager-thread", (request, pathVariables) -> threadName());
        }

        private static HttpResponse<?> threadName() {
            return HttpResponse.ok(Thread.currentThread().getName()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Marked {
        String value();
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MarkedTarget {
        @Executable
        @Marked("from the bean method")
        String target() {
            return "annotated";
        }
    }

    /**
     * A handler route that implements a bean method, with the annotations of the method.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnnotatedRoutes implements HttpRoutes {
        private final BeanContext beanContext;
        private final MarkedTarget target;

        AnnotatedRoutes(BeanContext beanContext, MarkedTarget target) {
            this.beanContext = beanContext;
            this.target = target;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/fn/annotated", (request, pathVariables) -> HttpResponse.ok(target.target()).contentType(MediaType.TEXT_PLAIN_TYPE))
                .annotationMetadata(beanContext.getBeanDefinition(MarkedTarget.class).getRequiredMethod("target").getAnnotationMetadata());
        }
    }

    @ServerFilter("/fn/annotated")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MarkedRouteFilter {
        @ResponseFilter
        void mark(RouteInfo<?> routeInfo, MutableHttpResponse<?> response) {
            routeInfo.getAnnotationMetadata().stringValue(Marked.class).ifPresent(value -> response.header("X-Marked", value));
            // like the return type of a method, the return type of the route has its annotations
            routeInfo.getReturnType().asArgument().getAnnotationMetadata().stringValue(Marked.class)
                .ifPresent(value -> response.header("X-Return-Marked", value));
        }
    }

    /**
     * Binds a handler to a declared route, like a route declared at compile time.
     */
    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DeclaredRoutes implements HttpRoutes {
        static final RouteDeclaration FIND = RouteDeclaration.of(HttpMethod.GET, "/fn/declared/{id}");

        HttpRouteSpec route;

        @Override
        public void routes(HttpRouteBuilder routes) {
            route = routes.handle(FIND, (request, pathVariables) -> HttpResponse.ok("declared " + pathVariables.getLong("id") + " "
                    + request.getAttribute(FILTER_THREAD, String.class).orElse("")).contentType(MediaType.TEXT_PLAIN_TYPE))
                .before("handler-filter", request -> {
                    request.setAttribute(FILTER_THREAD, Thread.currentThread().getName());
                    return null;
                })
                .after((request, response) -> response.header("X-Declared", "true"));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class DynamicHandlerRoutes implements RouteSource {
        private final RouteTableFactory tables;
        private volatile RouteTable current = RouteTable.empty();

        DynamicHandlerRoutes(RouteTableFactory tables) {
            this.tables = tables;
        }

        void enable() {
            current = tables.buildHttpRoutes(routes -> {
                routes.GET("/fn-dynamic/{+path}", (request, pathVariables) ->
                        HttpResponse.ok("dynamic " + request.getPath()).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .after((request, response) -> response.header("X-Table-Route", "true"));
                routes.handle(RouteDeclaration.of(HttpMethod.GET, "/fn-declared-dynamic/{id}"), (request, pathVariables) ->
                        HttpResponse.ok("dynamic declared " + pathVariables.getLong("id")).contentType(MediaType.TEXT_PLAIN_TYPE))
                    .after((request, response) -> response.header("X-Table-Route", "true"));
            });
        }

        @Override
        public RouteTable snapshot() {
            return current;
        }
    }

    @ServerFilter({"/fn/**", "/fn-dynamic/**"})
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FnFilter {
        @RequestFilter
        void filterRequest(HttpRequest<?> request) {
            if (request.getPath().equals("/fn/trace")) {
                request.setAttribute(TRACE, "global");
            }
        }

        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Fn-Filter", "true");
            String trace = response.getHeaders().get("X-Trace");
            if (trace != null) {
                response.getHeaders().set("X-Trace", trace + ",global");
            }
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Errors {
        @Error(global = true, exception = CheckedFailure.class)
        HttpResponse<String> checkedFailure(CheckedFailure failure) {
            return HttpResponse.<String>status(HttpStatus.CONFLICT).body("handled " + failure.getMessage()).contentType(MediaType.TEXT_PLAIN_TYPE);
        }
    }

    @Controller("/annotated-forms")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnnotatedForms {
        @Post(uri = "/data", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        String data(FormData form) {
            return "data " + form.getString("name") + " " + form.getInt("age", 1);
        }

        @Post(uri = "/parts", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> parts(FormParts parts) {
            // not closed here: the request closes the parts when it ends
            List<String> names = new ArrayList<>();
            return parts.part("name", part -> part.text(64).thenAccept(names::add))
                .thenApply(found -> "parts " + names);
        }
    }
}
