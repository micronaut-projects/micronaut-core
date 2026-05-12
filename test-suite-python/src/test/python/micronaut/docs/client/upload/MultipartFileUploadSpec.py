from typing import Annotated

import java
from jakarta.inject import Inject
from micronaut.http import HttpRequest, HttpResponse, MediaType
from micronaut.http.annotation import Controller, Post
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.http.multipart import CompletedFileUpload
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

# tag::multipartBodyImports[]
MultipartBody = java.type("io.micronaut.http.client.multipart.MultipartBody")
# end::multipartBodyImports[]

File = java.type("java.io.File")
FileWriter = java.type("java.io.FileWriter")
Flux = java.type("reactor.core.publisher.Flux")
String = java.type("java.lang.String")


@Controller("/multipart")
class MultipartController:

    @Post(value="/upload", consumes=MediaType.MULTIPART_FORM_DATA, produces=MediaType.TEXT_PLAIN)
    def upload(self, data: CompletedFileUpload) -> HttpResponse:
        return HttpResponse.ok("Uploaded " + str(len(data.getBytes())) + " bytes")


@MicronautTest
class MultipartFileUploadSpec:
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testMultipartFileRequestByteArray(self):
        # tag::file[]
        toWrite = "test file"
        file = File.createTempFile("data", ".txt")
        writer = FileWriter(file)
        writer.write(toWrite)
        writer.close()
        # end::file[]

        # tag::multipartBody[]
        requestBody = (
            MultipartBody.builder()  # <1>
            .addPart(  # <2>
                "data",
                file.getName(),
                MediaType.TEXT_PLAIN_TYPE,
                file,
            ).build()  # <3>
        )
        # end::multipartBody[]

        flowable = Flux.from_(
            self.client.exchange(
                # tag::request[]
                HttpRequest.POST("/multipart/upload", requestBody)  # <1>
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)  # <2>
                # end::request[]
                    .accept(MediaType.TEXT_PLAIN_TYPE),
                String,
            )
        )
        response = flowable.blockFirst()
        body = response.getBody().get()

        assert body == "Uploaded 9 bytes"

    @Test
    def testMultipartFileRequestByteArrayWithContentType(self):
        # tag::multipartBodyBytes[]
        requestBody = MultipartBody.builder() \
            .addPart("data", "sample.txt", MediaType.TEXT_PLAIN_TYPE, "test content".encode("utf-8")) \
            .build()
        # end::multipartBodyBytes[]

        flowable = Flux.from_(
            self.client.exchange(
                HttpRequest.POST("/multipart/upload", requestBody)
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE),
                String,
            )
        )
        response = flowable.blockFirst()
        body = response.getBody().get()

        assert body == "Uploaded 12 bytes"

    @Test
    def testMultipartFileRequestByteArrayWithoutContentType(self):
        toWrite = "test file"
        file = File.createTempFile("data", ".txt")
        writer = FileWriter(file)
        writer.write(toWrite)
        writer.close()

        flowable = Flux.from_(
            self.client.exchange(
                HttpRequest.POST("/multipart/upload", MultipartBody.builder().addPart("data", file.getName(), file).build())
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
                    .accept(MediaType.TEXT_PLAIN_TYPE),
                String,
            )
        )
        response = flowable.blockFirst()
        body = response.getBody().get()

        assert body == "Uploaded 9 bytes"
