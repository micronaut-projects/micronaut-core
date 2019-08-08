package io.micronaut.http.client.docs.basics;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.CustomHttpMethod;
import io.micronaut.http.annotation.Get;

@Controller("/webdav")
public class WebdavController {
    @Get
    public String get() {
        return "GET easy";
    }

    @CustomHttpMethod(method = "PROPFIND")
    public String propfind() {
        return "PROPFIND easy";
    }

    @CustomHttpMethod(method = "PROPPATCH")
    public String proppatch() {
        return "PROPPATCH easy";
    }

    @CustomHttpMethod(method = "PROPFIND", value = "/{name}")
    public String propfind(String name) {
        return "PROPFIND " + name;
    }

    @CustomHttpMethod(method = "REPORT", value = "/{name}")
    public Message report(String name) {
        return new Message("REPORT " + name);
    }

    @CustomHttpMethod(method = "LOCK")
    public Message report(@Body Message name) {
        return new Message("LOCK " + name.getText());
    }
}