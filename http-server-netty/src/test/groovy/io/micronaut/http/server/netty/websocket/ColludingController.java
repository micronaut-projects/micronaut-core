package io.micronaut.http.server.netty.websocket;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Produces;

@Controller("/binary/chat")
public class ColludingController {

    @Get("/{first}/{second}")
    @Produces(MediaType.TEXT_PLAIN)
    public String index(@PathVariable("first") String first, @PathVariable("second") String second) {
        return "Colluding!";
    }
}
