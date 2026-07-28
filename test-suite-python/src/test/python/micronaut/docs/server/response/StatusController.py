import java
from micronaut.context.annotation import Requires
from micronaut.http import HttpResponse, MediaType
from micronaut.http.annotation import Controller, Get, Status

HttpStatus = java.type("io.micronaut.http.HttpStatus")


@Requires(property="spec.name", value="httpstatus")
@Controller("/status")
class StatusController:

    # tag::atstatus[]
    @Status(HttpStatus.CREATED)
    @Get(produces=MediaType.TEXT_PLAIN)
    def index(self) -> HttpResponse:
        return HttpResponse.status(HttpStatus.CREATED).body("success")
    # end::atstatus[]

    # tag::httpstatus[]
    @Get("/http-status")
    def httpStatus(self) -> HttpStatus:
        return HttpStatus.CREATED
    # end::httpstatus[]

    # tag::httpresponse[]
    @Get(value="/http-response", produces=MediaType.TEXT_PLAIN)
    def httpResponse(self) -> HttpResponse:
        return HttpResponse.status(HttpStatus.CREATED).body("success")
    # end::httpresponse[]
