package io.micronaut.docs.server.consumes

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post

@Requires(property = "spec.name", value = "consumesspec")
//tag::clazz[]
@Controller("/test")
class TestController {

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON) // <1>
    @Post("/multiple-consumes")
    fun multipleConsumes(): HttpResponse<*> {
        return HttpResponse.ok<Any>()
    }

    @Post // <2>
    fun index(): HttpResponse<*> {
        return HttpResponse.ok<Any>()
    }
}
//end::clazz[]