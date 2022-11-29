package io.micronaut.tck.http.client;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Status;

@Requires(property = "spec.name", value = "HttpMethodDeleteTest")
@Controller("/delete")
class HttpMethodDeleteTestController {
    @Delete
    @Status(HttpStatus.NO_CONTENT)
    void index() {
    }
}
