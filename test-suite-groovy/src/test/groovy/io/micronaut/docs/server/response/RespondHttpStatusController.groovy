package io.micronaut.docs.server.response;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/respondHttpStatus")
class RespondHttpStatusController {

    //tag::respondHttpStatus[]
    @Get
    HttpStatus index() {
        HttpStatus.CREATED
    }
    //end::respondHttpStatus[]
}
