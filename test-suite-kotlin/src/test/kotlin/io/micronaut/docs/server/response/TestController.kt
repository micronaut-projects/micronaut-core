package io.micronaut.docs.server.response

import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces

//tag::clazz[]
@Controller("/test")
class TestController {

    @Get
    fun index(): HttpResponse<*> {
        return HttpResponse.ok<Any>().body("{\"msg\":\"This is JSON\"}")
    }

    @Produces(MediaType.TEXT_HTML) // <1>
    @Get("/html")
    fun html(): String {
        return "<html><title><h1>HTML</h1></title><body></body></html>"
    }
}
//end::clazz[]