package io.micronaut.http.client.docs.httpstatus;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/httpResponseStatus")
public class HttpResponseStatusController {

    //tag::httpResponseStatus[]
    @Get(produces = MediaType.TEXT_PLAIN)
    public HttpResponse index() {
        return HttpResponse.status(HttpStatus.CREATED).body("success");
    }
    //end::httpResponseStatus[]
}
