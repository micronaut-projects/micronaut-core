package io.micronaut.docs.http.server.stream

import io.micronaut.core.io.IOUtils
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

import java.nio.charset.StandardCharsets

@Controller("/stream")
class StreamController {

    // tag::write[]
    @Get(value = "/write", produces = MediaType.TEXT_PLAIN)
    InputStream write() {
        byte[] bytes = "test".getBytes(StandardCharsets.UTF_8);
        new ByteArrayInputStream(bytes) // <1>
    }
    // end::write[]

    // tag::read[]
    @Post(value = "/read", processes = MediaType.TEXT_PLAIN)
    @ExecuteOn(TaskExecutors.IO) // <1>
    String read(@Body InputStream inputStream) throws IOException { // <2>
        IOUtils.readText(new BufferedReader(new InputStreamReader(inputStream))) // <3>
    }
    // end::read[]
}
