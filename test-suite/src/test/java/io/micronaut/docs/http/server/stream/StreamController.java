package io.micronaut.docs.http.server.stream;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;

import java.io.InputStream;

@Controller("/stream")
public class StreamController {

    @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN)
    InputStream echoStream(@Body InputStream inputStream) { //<1>
        return inputStream; // <2>
    }

    @Post(value = "/echo", consumes = MediaType.TEXT_PLAIN)
    InputStream echoStream(@Body InputStream inputStream) { //<1>
        return inputStream; // <2>
    }
}
