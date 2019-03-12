package io.micronaut.http.server.netty.nativetransport;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/native-transport")
public class NativeTransportController {

    @Get
    String test() {
        return "works";
    }
}
