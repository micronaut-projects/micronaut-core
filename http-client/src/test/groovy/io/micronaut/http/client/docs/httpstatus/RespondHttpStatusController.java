package io.micronaut.http.client.docs.httpstatus;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/respondHttpStatus")
public class RespondHttpStatusController {

    //tag::respondHttpStatus[]
    @Get
    public HttpStatus index() {
        return HttpStatus.CREATED;
    }
    //end::respondHttpStatus[]
}
