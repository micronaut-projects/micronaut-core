package io.micronaut.docs.server.formdata;

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileControllerTest {

    private static final int MAX_FILE_SIZE = 512 * 1024;

    private static EmbeddedServer server;
    private static HttpClient client;

    @BeforeAll
    static void setupServer() {
        server = ApplicationContext.run(EmbeddedServer.class, Map.of(
            "spec.name", "ProfileControllerTest",
            "micronaut.server.multipart.max-file-size", MAX_FILE_SIZE
        ));
        client = server.getApplicationContext().createBean(HttpClient.class, server.getURL());
    }

    @AfterAll
    static void stopServer() {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void readsTheWholeForm() {
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("age", "30")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes("picture"))
            .build();
        assertEquals("Fred (30) sent avatar.png of 7 bytes", post("/profile/form", body));

        MultipartBody withoutAge = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes("picture"))
            .build();
        assertEquals("Fred (18) sent avatar.png of 7 bytes", post("/profile/form", withoutAge));
    }

    @Test
    void aMissingFileIsABadRequest() {
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .build();
        assertEquals(HttpStatus.BAD_REQUEST, status("/profile/form", body));
    }

    @Test
    void bindsTheFilesAndFieldsOfTheForm() {
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes("picture"))
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, bytes("cv"))
            .addPart("documents", "letter.pdf", MediaType.APPLICATION_PDF_TYPE, bytes("letter"))
            .build();
        assertEquals("Fred sent avatar.png without a cover and 2 documents", post("/profile/arguments", body));

        MultipartBody withCover = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes("picture"))
            .addPart("cover", "cover.png", MediaType.IMAGE_PNG_TYPE, bytes("cover"))
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, bytes("cv"))
            .build();
        assertEquals("Fred sent avatar.png with cover.png and 1 documents", post("/profile/arguments", withCover));
    }

    @Test
    void aFileLargerThanTheMaximumFileSizeIsTooLarge() {
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, new byte[2 * MAX_FILE_SIZE])
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, bytes("cv"))
            .build();
        assertEquals(HttpStatus.REQUEST_ENTITY_TOO_LARGE, status("/profile/arguments", body));
    }

    @Test
    void streamsAPart() {
        MultipartBody body = MultipartBody.builder()
            .addPart("title", "Holiday")
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, new byte[256 * 1024])
            .build();
        assertEquals("Holiday stored holiday.mp4", post("/profile/video", body));
    }

    @Test
    void aFieldSentAfterAStreamedPartIsABadRequest() {
        MultipartBody body = MultipartBody.builder()
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, new byte[256 * 1024])
            .addPart("title", "Holiday")
            .build();
        assertEquals(HttpStatus.BAD_REQUEST, status("/profile/video", body));
    }

    @Test
    void readsThePartsOneByOne() {
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes("picture"))
            .addPart("age", "30")
            .build();
        assertEquals("name=Fred, avatar stored, age=30, ", post("/profile/parts", body));
    }

    @Test
    void aFilterReadsTheFormBeforeTheController() {
        MultipartBody accepted = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("terms", "true")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes("picture"))
            .build();
        assertEquals("Welcome Fred, your avatar has 7 bytes", post("/signup", accepted));

        MultipartBody refused = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes("picture"))
            .build();
        assertEquals(HttpStatus.BAD_REQUEST, status("/signup", refused));
    }

    private static String post(String uri, MultipartBody body) {
        return client.toBlocking().retrieve(HttpRequest.POST(uri, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE));
    }

    private static HttpStatus status(String uri, MultipartBody body) {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> post(uri, body));
        return e.getStatus();
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
