package io.micronaut.docs.server.formdata

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.client.multipart.MultipartBody
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ProfileControllerSpec extends Specification {

    static final int MAX_FILE_SIZE = 512 * 1024

    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
        'spec.name': 'ProfileControllerSpec',
        'micronaut.server.multipart.max-file-size': MAX_FILE_SIZE
    ])
    @Shared @AutoCleanup HttpClient client = server.applicationContext.createBean(HttpClient, server.URL)

    void "reads the whole form"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("age", "30")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".bytes)
            .build()
        MultipartBody withoutAge = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".bytes)
            .build()

        expect:
        post("/profile/form", body) == "Fred (30) sent avatar.png of 7 bytes"
        post("/profile/form", withoutAge) == "Fred (18) sent avatar.png of 7 bytes"
    }

    void "a missing file is a bad request"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .build()

        expect:
        status("/profile/form", body) == HttpStatus.BAD_REQUEST
    }

    void "binds the files and fields of the form"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".bytes)
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".bytes)
            .addPart("documents", "letter.pdf", MediaType.APPLICATION_PDF_TYPE, "letter".bytes)
            .build()
        MultipartBody withCover = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".bytes)
            .addPart("cover", "cover.png", MediaType.IMAGE_PNG_TYPE, "cover".bytes)
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".bytes)
            .build()

        expect:
        post("/profile/arguments", body) == "Fred sent avatar.png without a cover and 2 documents"
        post("/profile/arguments", withCover) == "Fred sent avatar.png with cover.png and 1 documents"
    }

    void "a file larger than the maximum file size is too large"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, new byte[2 * MAX_FILE_SIZE])
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".bytes)
            .build()

        expect:
        status("/profile/arguments", body) == HttpStatus.REQUEST_ENTITY_TOO_LARGE
    }

    void "streams a part"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("title", "Holiday")
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, new byte[256 * 1024])
            .build()

        expect:
        post("/profile/video", body) == "Holiday stored holiday.mp4"
    }

    void "a field sent after a streamed part is a bad request"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, new byte[256 * 1024])
            .addPart("title", "Holiday")
            .build()

        expect:
        status("/profile/video", body) == HttpStatus.BAD_REQUEST
    }

    void "reads the parts one by one"() {
        given:
        MultipartBody body = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".bytes)
            .addPart("age", "30")
            .build()

        expect:
        post("/profile/parts", body) == "name=Fred, avatar stored, age=30, "
    }

    void "a filter reads the form before the controller"() {
        given:
        MultipartBody accepted = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("terms", "true")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".bytes)
            .build()
        MultipartBody refused = MultipartBody.builder()
            .addPart("name", "Fred")
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".bytes)
            .build()

        expect:
        post("/signup", accepted) == "Welcome Fred, your avatar has 7 bytes"
        status("/signup", refused) == HttpStatus.BAD_REQUEST
    }

    private String post(String uri, MultipartBody body) {
        client.toBlocking().retrieve(HttpRequest.POST(uri, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE))
    }

    private HttpStatus status(String uri, MultipartBody body) {
        try {
            post(uri, body)
            throw new AssertionError("expected an error response from " + uri)
        } catch (HttpClientResponseException e) {
            return e.status
        }
    }
}
