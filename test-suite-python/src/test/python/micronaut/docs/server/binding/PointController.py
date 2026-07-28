import java
from micronaut.context.annotation import Requires
from micronaut.http import MediaType
from micronaut.http.annotation import Consumes, Controller, Post, Status

from .Point import Point

HttpStatus = java.type("io.micronaut.http.HttpStatus")


@Requires(property="spec.name", value="PointControllerTest")
# tag::class[]
@Controller("/point")
class PointController:

    @Post(uri="/no-body-json")
    @Status(HttpStatus.CREATED)
    def no_body_json(self, x: int, y: int) -> Point:  # <1>
        return Point(x, y)

    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Post("/no-body-form")
    @Status(HttpStatus.CREATED)
    def no_body_form(self, x: int, y: int) -> Point:  # <2>
        return Point(x, y)
# end::class[]
