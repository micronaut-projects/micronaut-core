package io.micronaut.docs.server.error

import com.fasterxml.jackson.core.JsonParseException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.hateoas.JsonError
import io.micronaut.http.hateoas.Link

@Controller("/people")
class PersonController {

    // tag::localError[]
    @Error
    fun jsonError(
        request: HttpRequest<String>,
        jsonParseException: JsonParseException
    ): HttpResponse<JsonError> { // <1>
        val error = JsonError("Invalid JSON: ${jsonParseException.message}") // <2>
            .link(Link.SELF, Link.of(request.uri))
        return HttpResponse.status<JsonError>(HttpStatus.BAD_REQUEST, "Fix your JSON").body(error) // <3>
    }
    // end::localError[]

}