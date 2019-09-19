
package io.micronaut.docs.server.response

import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get

@Requires(property = "spec.name", value = "httpstatus")
@Controller("/httpResponseStatus")
class HttpResponseStatusController {

    //tag::httpResponseStatus[]
    @Get(produces = [MediaType.TEXT_PLAIN])
    fun index(): HttpResponse<*> {
        return HttpResponse.status<Any>(HttpStatus.CREATED).body("success")
    }
    //end::httpResponseStatus[]
}
