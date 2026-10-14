from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.context.annotation import Property
from micronaut.http import HttpRequest, HttpStatus, MediaType
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

Files = java.type("java.nio.file.Files")
Map = java.type("java.util.Map")
MultipartBody = java.type("io.micronaut.http.client.multipart.MultipartBody")
Path = java.type("java.nio.file.Path")
String = java.type("java.lang.String")
System = java.type("java.lang.System")


@Property(name="spec.name", value="FormRoutesTest")
@Property(name="uploads.directory", value="${java.io.tmpdir}")
@MicronautTest
class FormRoutesTest:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def a_url_encoded_form(self):
        http = self.client.toBlocking()
        assert http.retrieve(HttpRequest.POST("/forms/signup", Map.of("name", "Ada", "age", "36"))
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) == "Welcome Ada, 36"
        assert http.retrieve(HttpRequest.POST("/forms/signup", Map.of("name", "Bob"))
                             .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) == "Welcome Bob, 18"

    @Test
    def a_collected_multipart_form(self):
        body = MultipartBody.builder() \
            .addPart("name", "Ada") \
            .addPart("avatar", "ada.png", MediaType.IMAGE_PNG_TYPE, bytes(2048)) \
            .build()
        assert self.client.toBlocking().retrieve(HttpRequest.POST("/forms/profile", body)
                                                 .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)) == "Ada sent ada.png, 2048 bytes"

    @Test
    def a_streamed_file_part(self):
        content = "line\n" * 10_000
        body = MultipartBody.builder() \
            .addPart("file", "lines.txt", MediaType.TEXT_PLAIN_TYPE, content.encode("utf-8")) \
            .build()
        response = self.client.toBlocking().exchange(HttpRequest.POST("/forms/upload", body)
                                                     .contentType(MediaType.MULTIPART_FORM_DATA_TYPE), String)
        assert response.getStatus() == HttpStatus.CREATED
        file = Path.of(System.getProperty("java.io.tmpdir"), response.body())
        try:
            assert Files.readString(file) == content
        finally:
            Files.deleteIfExists(file)
