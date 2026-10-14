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
package io.micronaut.http.server.netty.binding;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The members of a {@link RequestBean} that are taken from the body of the request are bound
 * like the parameters of the route: the route waits for a body that is still arriving. Each
 * request is sent whole, and with the end of its body withheld until the route was matched.
 */
class RequestBeanFormTest {
    private static final String SPEC_NAME = "RequestBeanFormTest";
    private static final String BOUNDARY = "request-bean";
    private static final String URL_ENCODED = MediaType.APPLICATION_FORM_URLENCODED;
    private static final String MULTIPART = MediaType.MULTIPART_FORM_DATA + "; boundary=" + BOUNDARY;
    private static final String FORM = "name=Fred&city=Prague";
    private static final String MULTIPART_FORM = "--" + BOUNDARY + "\r\n"
        + "Content-Disposition: form-data; name=\"name\"\r\n"
        + "\r\n"
        + "Fred\r\n"
        + "--" + BOUNDARY + "\r\n"
        + "Content-Disposition: form-data; name=\"city\"\r\n"
        + "\r\n"
        + "Prague\r\n"
        + "--" + BOUNDARY + "\r\n"
        + "Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.txt\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "picture\r\n"
        + "--" + BOUNDARY + "--\r\n";

    private static ApplicationContext ctx;
    private static EmbeddedServer server;

    @BeforeAll
    static void start() {
        ctx = ApplicationContext.run(Map.of("spec.name", SPEC_NAME));
        server = ctx.getBean(EmbeddedServer.class).start();
    }

    @AfterAll
    static void stop() {
        ctx.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFormDataOfARequestBean(boolean withheld) throws Exception {
        assertEquals("Fred Prague 7", exchange("/request-bean/data", URL_ENCODED, FORM, withheld));
        assertEquals("Fred Prague 7", exchange("/request-bean/data", MULTIPART, MULTIPART_FORM, withheld));
        // the same for a parameter of the route
        assertEquals("Fred Prague 7", exchange("/request-bean/data-parameter", URL_ENCODED, FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFormDataOfARequestBeanWithSetters(boolean withheld) throws Exception {
        assertEquals("Fred Prague 7", exchange("/request-bean/data-setters", URL_ENCODED, FORM, withheld));
        assertEquals("Fred Prague 7", exchange("/request-bean/data-setters", MULTIPART, MULTIPART_FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFormDataOfARequestBeanOfABlockingRoute(boolean withheld) throws Exception {
        String thread = exchange("/request-bean/data-blocking", URL_ENCODED, FORM, withheld);
        // the route runs on its executor after it waited for the form
        assertEquals("Fred Prague 7 blocking", thread.replaceAll("blocking.*", "blocking"), thread);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theTextFieldsOfARequestBean(boolean withheld) throws Exception {
        assertEquals("Fred Prague none", exchange("/request-bean/text", URL_ENCODED, FORM, withheld));
        assertEquals("Fred Prague none", exchange("/request-bean/text", MULTIPART, MULTIPART_FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFileUploadOfARequestBean(boolean withheld) throws Exception {
        assertEquals("Fred avatar.txt picture", exchange("/request-bean/file", MULTIPART, MULTIPART_FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFormDataAndItsFieldsOfARequestBean(boolean withheld) throws Exception {
        assertEquals("Fred Prague avatar.txt picture", exchange("/request-bean/all", MULTIPART, MULTIPART_FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void anAsyncRequestBodyAndThePathVariablesOfARequestBean(boolean withheld) throws Exception {
        assertEquals("5 Fred Prague", exchange("/request-bean/async/5", URL_ENCODED, FORM, withheld));
        assertEquals("5 Fred Prague", exchange("/request-bean/async/5", MULTIPART, MULTIPART_FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aMissingRequiredFieldOfARequestBeanIsABadRequest(boolean withheld) throws Exception {
        String response = exchange("/request-bean/text", URL_ENCODED, "name=Fred&other=Prague", withheld);
        assertEquals("HTTP/1.1 400 Bad Request", response.substring(0, Math.min(response.length(), 24)), response);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aCompletedFileUploadOfARequestBean(boolean withheld) throws Exception {
        assertEquals("avatar.txt picture", exchange("/request-bean/completed", MULTIPART, MULTIPART_FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void theFormPartsOfARequestBean(boolean withheld) throws Exception {
        assertEquals("name=Fred;city=Prague;", exchange("/request-bean/parts", URL_ENCODED, FORM, withheld));
        assertEquals("name=Fred;city=Prague;avatar=picture;", exchange("/request-bean/parts", MULTIPART, MULTIPART_FORM, withheld));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aFormPartOfARequestBean(boolean withheld) throws Exception {
        assertEquals("city=Prague", exchange("/request-bean/part", URL_ENCODED, FORM, withheld));
        assertEquals("city=Prague", exchange("/request-bean/part", MULTIPART, MULTIPART_FORM, withheld));
    }

    /**
     * Send a request, whole or with the end of its body withheld until the server matched the
     * route and bound its arguments, and read the body of the response.
     */
    private static String exchange(String path, String contentType, String content, boolean withheld) throws Exception {
        try (Socket socket = new Socket(server.getHost(), server.getPort())) {
            socket.setSoTimeout(30_000);
            OutputStream out = socket.getOutputStream();
            byte[] body = content.getBytes(StandardCharsets.UTF_8);
            byte[] head = ("POST " + path + " HTTP/1.1\r\n"
                + "Host: " + server.getHost() + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "X-Id: 7\r\n"
                + "Connection: close\r\n"
                + "\r\n").getBytes(StandardCharsets.US_ASCII);
            if (withheld) {
                int first = body.length - 5;
                out.write(head);
                out.write(body, 0, first);
                out.flush();
                // the route is matched and its arguments are bound from the head of the request
                Thread.sleep(300);
                out.write(body, first, body.length - first);
            } else {
                ByteArrayOutputStream whole = new ByteArrayOutputStream();
                whole.write(head);
                whole.write(body);
                out.write(whole.toByteArray());
            }
            out.flush();
            return responseBody(socket.getInputStream());
        }
    }

    private static String responseBody(InputStream in) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int b = in.read();
            if (b < 0) {
                break;
            }
            bytes.write(b);
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3)) ? matched + 1 : 0;
        }
        String head = bytes.toString(StandardCharsets.US_ASCII);
        String status = head.substring(0, Math.max(0, head.indexOf("\r\n")));
        String prefix = status.contains(" 200 ") ? "" : status + " ";
        for (String line : head.split("\r\n")) {
            if (line.regionMatches(true, 0, "content-length:", 0, 15)) {
                return prefix + new String(in.readNBytes(Integer.parseInt(line.substring(15).trim())), StandardCharsets.UTF_8);
            }
        }
        return prefix + new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Introspected
    record DataBean(FormData form, @Header("X-Id") String id) {
    }

    @Introspected
    static class SettersBean {
        private @Nullable FormData form;
        @Header("X-Id")
        private @Nullable String id;

        public @Nullable FormData getForm() {
            return form;
        }

        public void setForm(@Nullable FormData form) {
            this.form = form;
        }

        public @Nullable String getId() {
            return id;
        }

        public void setId(@Nullable String id) {
            this.id = id;
        }
    }

    @Introspected
    record TextBean(@Part("name") String name, @Part("city") String city, @Nullable @Part("country") String country) {
    }

    @Introspected
    record FileBean(@Part("avatar") FileUpload avatar, @Part("name") String name) {
    }

    @Introspected
    record AllBean(FormData form, @Part("avatar") FileUpload avatar, @Part("city") String city) {
    }

    @Introspected
    record AsyncBean(AsyncRequestBody body, PathVariables path) {
    }

    @Introspected
    record PartsBean(FormParts parts, @Header("X-Id") String id) {
    }

    @Introspected
    record PartBean(@Part("city") FormPart city) {
    }

    @Introspected
    record CompletedBean(@Part("avatar") CompletedFileUpload avatar) {
    }

    @Controller("/request-bean")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class RequestBeanController {

        @Post(uri = "/data", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String data(@RequestBean DataBean bean) {
            return bean.form().getString("name") + " " + bean.form().getString("city") + " " + bean.id();
        }

        @Post(uri = "/data-parameter", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String dataParameter(FormData form, @Header("X-Id") String id) {
            return form.getString("name") + " " + form.getString("city") + " " + id;
        }

        @Post(uri = "/data-setters", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String dataSetters(@RequestBean SettersBean bean) {
            FormData form = bean.getForm();
            return form == null ? "no form" : form.getString("name") + " " + form.getString("city") + " " + bean.getId();
        }

        @Post(uri = "/data-blocking", consumes = MediaType.APPLICATION_FORM_URLENCODED, produces = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.BLOCKING)
        String dataBlocking(@RequestBean DataBean bean) {
            String thread = Thread.currentThread().getName();
            return bean.form().getString("name") + " " + bean.form().getString("city") + " " + bean.id()
                + (thread.contains("eventLoop") ? " " + thread : " blocking " + thread);
        }

        @Post(uri = "/text", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        String text(@RequestBean TextBean bean) {
            return bean.name() + " " + bean.city() + " " + (bean.country() == null ? "none" : bean.country());
        }

        @Post(uri = "/file", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> file(@RequestBean FileBean bean) {
            return bean.avatar().bytes(1024).thenApply(bytes -> bean.name() + " " + bean.avatar().fileName() + " " + new String(bytes, StandardCharsets.UTF_8));
        }

        @Post(uri = "/all", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> all(@RequestBean AllBean bean) {
            return bean.avatar().bytes(1024).thenApply(bytes -> bean.form().getString("name") + " " + bean.city() + " "
                + bean.form().getFile("avatar").fileName() + " " + new String(bytes, StandardCharsets.UTF_8));
        }

        @Post(uri = "/async/{id}", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> async(@RequestBean AsyncBean bean) {
            return bean.body().form().thenApply(form -> bean.path().getString("id") + " " + form.getString("name") + " " + form.getString("city"));
        }

        @Post(uri = "/parts", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> parts(@RequestBean PartsBean bean) {
            StringBuilder result = new StringBuilder();
            return bean.parts().forEach(part -> part.text().thenAccept(text -> result.append(part.name()).append('=').append(text).append(';')))
                .thenApply(done -> result.toString());
        }

        @Post(uri = "/part", consumes = {MediaType.APPLICATION_FORM_URLENCODED, MediaType.MULTIPART_FORM_DATA}, produces = MediaType.TEXT_PLAIN)
        CompletionStage<String> part(@RequestBean PartBean bean) {
            return bean.city().text().thenApply(text -> bean.city().name() + "=" + text);
        }

        @Post(uri = "/completed", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
        String completed(@RequestBean CompletedBean bean) throws IOException {
            return bean.avatar().getFilename() + " " + new String(bean.avatar().getBytes(), StandardCharsets.UTF_8);
        }
    }
}
