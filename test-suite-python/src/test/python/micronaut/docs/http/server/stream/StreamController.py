from typing import Annotated

import java
from micronaut.core.io import IOUtils
from micronaut.http import MediaType
from micronaut.http.annotation import Body, Controller, Get, Post
from micronaut.scheduling import TaskExecutors
from micronaut.scheduling.annotation import ExecuteOn

BufferedReader = java.type("java.io.BufferedReader")
ByteArrayInputStream = java.type("java.io.ByteArrayInputStream")
InputStream = java.type("java.io.InputStream")
InputStreamReader = java.type("java.io.InputStreamReader")
String = java.type("java.lang.String")
StandardCharsets = java.type("java.nio.charset.StandardCharsets")


@Controller("/stream")
class StreamController:

    # tag::write[]
    @Get(value="/write", produces=MediaType.TEXT_PLAIN)
    def write(self) -> InputStream:
        bytes = bytearray(b"test")
        return ByteArrayInputStream(bytes)  # <1>
    # end::write[]

    # tag::read[]
    @Post(value="/read", processes=MediaType.TEXT_PLAIN)
    @ExecuteOn(TaskExecutors.IO)  # <1>
    def read(self, inputStream: Annotated[InputStream, Body]) -> str:  # <2>
        return IOUtils.readText(BufferedReader(InputStreamReader(inputStream)))  # <3>
    # end::read[]
