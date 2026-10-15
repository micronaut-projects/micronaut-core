from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

MultipartBody = java.type("io.micronaut.http.client.multipart.MultipartBody")
String = java.type("java.lang.String")

MAX_FILE_SIZE = 512 * 1024


@Property(name="spec.name", value="ProfileControllerSpec")
@Property(name="micronaut.server.multipart.max-file-size", value="524288")
@MicronautTest
class ProfileControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def reads_the_whole_form(self):
        body = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("age", "30") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".encode("utf-8")) \
            .build()
        assert self.post("/profile/form", body) == "Fred (30) sent avatar.png of 7 bytes"

        without_age = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".encode("utf-8")) \
            .build()
        assert self.post("/profile/form", without_age) == "Fred (18) sent avatar.png of 7 bytes"

    @Test
    def a_missing_file_is_a_bad_request(self):
        body = MultipartBody.builder().addPart("name", "Fred").build()
        assert self.status("/profile/form", body) == HttpStatus.BAD_REQUEST

    @Test
    def binds_the_files_and_fields_of_the_form(self):
        body = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".encode("utf-8")) \
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".encode("utf-8")) \
            .addPart("documents", "letter.pdf", MediaType.APPLICATION_PDF_TYPE, "letter".encode("utf-8")) \
            .build()
        assert self.post("/profile/arguments", body) == "Fred sent avatar.png without a cover and 2 documents"

        with_cover = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".encode("utf-8")) \
            .addPart("cover", "cover.png", MediaType.IMAGE_PNG_TYPE, "cover".encode("utf-8")) \
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".encode("utf-8")) \
            .build()
        assert self.post("/profile/arguments", with_cover) == "Fred sent avatar.png with cover.png and 1 documents"

    @Test
    def a_file_larger_than_the_maximum_file_size_is_too_large(self):
        body = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, bytes(2 * MAX_FILE_SIZE)) \
            .addPart("documents", "cv.pdf", MediaType.APPLICATION_PDF_TYPE, "cv".encode("utf-8")) \
            .build()
        assert self.status("/profile/arguments", body) == HttpStatus.REQUEST_ENTITY_TOO_LARGE

    @Test
    def streams_a_part(self):
        body = MultipartBody.builder() \
            .addPart("title", "Holiday") \
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, bytes(256 * 1024)) \
            .build()
        assert self.post("/profile/video", body) == "Holiday stored holiday.mp4"

    @Test
    def a_field_sent_after_a_streamed_part_is_a_bad_request(self):
        body = MultipartBody.builder() \
            .addPart("video", "holiday.mp4", MediaType.APPLICATION_OCTET_STREAM_TYPE, bytes(256 * 1024)) \
            .addPart("title", "Holiday") \
            .build()
        assert self.status("/profile/video", body) == HttpStatus.BAD_REQUEST

    @Test
    def reads_the_parts_one_by_one(self):
        body = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".encode("utf-8")) \
            .addPart("age", "30") \
            .build()
        assert self.post("/profile/parts", body) == "name=Fred, avatar stored, age=30, "

    @Test
    def a_filter_reads_the_form_before_the_controller(self):
        accepted = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("terms", "true") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".encode("utf-8")) \
            .build()
        assert self.post("/signup", accepted) == "Welcome Fred, your avatar has 7 bytes"

        refused = MultipartBody.builder() \
            .addPart("name", "Fred") \
            .addPart("avatar", "avatar.png", MediaType.IMAGE_PNG_TYPE, "picture".encode("utf-8")) \
            .build()
        assert self.status("/signup", refused) == HttpStatus.BAD_REQUEST

    def post(self, uri: str, body) -> str:
        return self.client.toBlocking().retrieve(
            HttpRequest.POST(uri, body).contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            String,
        )

    def status(self, uri: str, body):
        try:
            self.post(uri, body)
        except HttpClientResponseException as e:
            return e.getStatus()
        raise AssertionError("expected an error response from " + uri)
