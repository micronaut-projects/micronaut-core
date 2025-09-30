package io.micronaut.docs.server.request_scope

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue

@Requires(property = "spec.name", value = "TestControllerTest")
@Controller
class DemoController(
    private val requestScopeClass: RequestScopeClass
) {

    @Get("/testEndpoint")
    fun testEndpoint(
        @QueryValue("text") text: String?
    ): HttpResponse<DemoObject> {
        if (text != null) {
            requestScopeClass.text = text
        }
        requestScopeClass.list.add("listEntry")
        val list = requestScopeClass.list
        return HttpResponse.ok(
            DemoObject(
                text = requestScopeClass.text ?: "defaultText",
                list = list
            )
        )
    }
}
