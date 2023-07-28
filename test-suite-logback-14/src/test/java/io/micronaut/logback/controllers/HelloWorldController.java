package io.micronaut.logback.controllers;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class HelloWorldController {

    public static final String RESPONSE = "Hello world!";
    public static final String LOG_MESSAGE = "inside hello world";

    private static final Logger LOG = LoggerFactory.getLogger(HelloWorldController.class);

    @Get
    String index() {
        LOG.trace(LOG_MESSAGE);
        return RESPONSE;
    }
}
