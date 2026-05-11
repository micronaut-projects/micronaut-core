from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.client.exceptions import HttpClientResponseException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

MultipartBody = java.type("io.micronaut.http.client.multipart.MultipartBody")
Map = java.type("java.util.Map")
String = java.type("java.lang.String")


@MicronautTest
class UploadControllerSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testFileUpload(self):
        body = self.multipart_file_body("file", "file.json", "{\"title\":\"Foo\"}")
        response = self.exchange_upload("/", body)

        assert response.code() == HttpStatus.OK.getCode()
        assert response.getBody().get() == "Uploaded"

    @Test
    def testFileUploadOutputStream(self):
        body = self.multipart_file_body("file", "file.json", "{\"title\":\"Foo\"}")
        response = self.exchange_upload("/outputStream", body)

        assert response.code() == HttpStatus.OK.getCode()
        assert response.getBody().get() == "Uploaded"

    @Test
    def testCompletedFileUpload(self):
        body = self.multipart_file_body("file", "file.json", "{\"title\":\"Foo\"}")
        response = self.exchange_upload("/completed", body)

        assert response.code() == HttpStatus.OK.getCode()
        assert response.getBody().get() == "Uploaded"

    @Test
    def testCompletedFileUploadWithFilenameButNoBytes(self):
        body = self.multipart_file_body("file", "file.json", "")
        response = self.exchange_upload("/completed", body)

        assert response.code() == HttpStatus.OK.getCode()
        assert response.getBody().get() == "Uploaded"

    @Test
    def testCompletedFileUploadNoNameWithBytes(self):
        body = self.multipart_file_body("file", "", "{\"title\":\"Foo\"}")
        self.assert_upload_error(
            "/completed",
            body,
            "Field [file] was expected to be a file upload, but is missing a file name",
        )

    @Test
    def testCompletedFileUploadWithNoFileNameAndNoBytes(self):
        body = self.multipart_file_body("file", "", "")
        self.assert_upload_error(
            "/completed",
            body,
            "Field [file] was expected to be a file upload, but is missing a file name",
        )

    @Test
    def testCompletedFileUploadWithNoPart(self):
        body = self.multipart_file_body("filex", "", "")
        self.assert_upload_error(
            "/completed",
            body,
            "Required argument [CompletedFileUpload file] not specified",
        )

    @Test
    def testFileBytesUpload(self):
        body = MultipartBody.builder().addPart(
            "file",
            "file.json",
            MediaType.TEXT_PLAIN_TYPE,
            "some data".encode("utf-8"),
        ).addPart("fileName", "bar").build()
        response = self.exchange_upload("/bytes", body)

        assert response.code() == HttpStatus.OK.getCode()
        assert response.getBody().get() == "Uploaded"

    def multipart_file_body(self, name: str, filename: str, content: str):
        return MultipartBody.builder().addPart(
            name,
            filename,
            MediaType.APPLICATION_JSON_TYPE,
            content.encode("utf-8"),
        ).build()

    def exchange_upload(self, path: str, body):
        return self.client.toBlocking().exchange(
            HttpRequest.POST("/upload" + path, body)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.TEXT_PLAIN_TYPE),
            String,
        )

    def assert_upload_error(self, path: str, body, expected_message: str):
        try:
            self.exchange_upload(path, body)
            assert False
        except HttpClientResponseException as e:
            response_body = e.getResponse().getBody(Map).get()
            embedded = response_body.get("_embedded")
            message = embedded.get("errors")[0].get("message")
            assert message == expected_message
