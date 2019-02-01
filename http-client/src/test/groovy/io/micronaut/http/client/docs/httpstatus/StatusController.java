package io.micronaut.http.client.docs.httpstatus;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Status;

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/status")
public class StatusController {

    @Get(value = "/simple", produces = MediaType.TEXT_PLAIN)
    public String simple() {
        return "success";
    }

    //tag::atstatus[]
    @Status(HttpStatus.CREATED)
    @Get(produces = MediaType.TEXT_PLAIN)
    public String index() {
        return "success";
    }
    //end::atstatus[]

    @Status(HttpStatus.CREATED)
    @Get(value = "/voidreturn")
    public void voidReturn() {
    }

    @Status(HttpStatus.NOT_FOUND)
    @Get(value = "/simple404", produces = MediaType.TEXT_PLAIN)
    public String simple404() {
        return "success";
    }
}
