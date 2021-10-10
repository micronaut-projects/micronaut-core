package io.micronaut.docs.server.consumes;

//tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
//end::imports[]

@Requires(property = "spec.name", value = "consumesspec")
//tag::clazz[]
@Controller("/consumes")
public class ConsumesController {

    @Post // <1>
    public HttpResponse index() {
        return HttpResponse.ok();
    }

    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON}) // <2>
    @Post("/multiple")
    public HttpResponse multipleConsumes() {
        return HttpResponse.ok();
    }

    @Post(value = "/member", consumes = MediaType.TEXT_PLAIN) // <3>
    public HttpResponse consumesMember() {
        return HttpResponse.ok();
    }
}
//end::clazz[]
