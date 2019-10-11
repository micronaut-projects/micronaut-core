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

    // tag::globalError[]
    @Error // <1>
    fun error(request: HttpRequest<String>, e: Throwable): HttpResponse<JsonError> {
        val error = JsonError("Bad Things Happened: ${e.message}") // <2>
        return HttpResponse.serverError<JsonError>().body(error) // <3>
    }
    // end::globalError[]

    // tag::statusError[]
    @Error(status = HttpStatus.NOT_FOUND)
    fun notFound(request: HttpRequest<String>): HttpResponse<JsonError> { // <1>
        val error = JsonError("Page Not Found") // <2>
            .link(Link.SELF, Link.of(request.uri))

        return HttpResponse.notFound<JsonError>().body(error) // <3>
    }
    // end::statusError[]
}