package io.micronaut.docs.server.binding;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;

@Requires(property = "spec.name", value = "PointControllerTest")
// tag::class[]
@Controller("/point")
public class PointController {

    @Post(uri = "/no-body-json")
    @Status(HttpStatus.CREATED)
    Point noBodyJson(Integer x, Integer y) { // (1)
        return new Point(x,y);
    }

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/no-body-form")
    @Status(HttpStatus.CREATED)
    Point noBodyForm(Integer x, Integer y) {  // (2)
        return new Point(x,y);
    }
}
// end::class[]
