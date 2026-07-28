from typing import Annotated

from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.validation.constraints import NotBlank
from micronaut.http import HttpResponse
from micronaut.http.annotation import Controller, Get
from micronaut.validation import Validated
# end::imports[]


@Requires(property="spec.name", value="datavalidationparams")
# tag::clazz[]
@Validated  # <1>
@Controller("/email")
class EmailController:

    @Get("/send")
    def send(
        self,
        recipient: Annotated[str, NotBlank],  # <2>
        subject: Annotated[str, NotBlank],  # <2>
    ) -> HttpResponse:
        return HttpResponse.ok({"msg": "OK"})
# end::clazz[]
