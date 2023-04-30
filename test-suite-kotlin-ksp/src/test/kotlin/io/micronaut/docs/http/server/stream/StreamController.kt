package io.micronaut.docs.http.server.stream

import io.micronaut.core.io.IOUtils
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.scheduling.TaskExecutors
import java.io.*
import java.nio.charset.StandardCharsets

@Controller("/stream")
class StreamController {

    // tag::write[]
    @Get(value = "/write", produces = [MediaType.TEXT_PLAIN])
    fun write(): InputStream {
        val bytes = "test".toByteArray(StandardCharsets.UTF_8)
        return ByteArrayInputStream(bytes) // <1>
    }
    // end::write[]

    // tag::read[]
    @Post(value = "/read", processes = [MediaType.TEXT_PLAIN])
    @ExecuteOn(TaskExecutors.IO) // <1>
    fun read(@Body inputStream: InputStream): String { // <2>
        return IOUtils.readText(BufferedReader(InputStreamReader(inputStream))) // <3>
    }
    // end::read[]
}
