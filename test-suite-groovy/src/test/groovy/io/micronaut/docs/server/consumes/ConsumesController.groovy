package io.micronaut.docs.server.consumes

//tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
//end::imports[]

@Requires(property = "spec.name", value = "consumesspec")
//tag::clazz[]
@Controller("/consumes")
class ConsumesController {

    @Post // <1>
    HttpResponse index() {
        HttpResponse.ok()
    }

    @Consumes([MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON]) // <2>
    @Post("/multiple")
    HttpResponse multipleConsumes() {
        HttpResponse.ok()
    }

    @Post(value = "/member", consumes = MediaType.TEXT_PLAIN) // <3>
    HttpResponse consumesMember() {
        HttpResponse.ok()
    }
}
//end::clazz[]
