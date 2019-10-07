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
    fun index(): HttpResponse<*> {
        return HttpResponse.ok<Any>()
    }

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED, MediaType.APPLICATION_JSON) // <2>
    @Post("/multiple")
    fun multipleConsumes(): HttpResponse<*> {
        return HttpResponse.ok<Any>()
    }

    @Post(value = "/member", consumes = [MediaType.TEXT_PLAIN]) // <3>
    fun consumesMember(): HttpResponse<*> {
        return HttpResponse.ok<Any>()
    }
}
//end::clazz[]
