from typing import Annotated

from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.validation import Valid
from micronaut.http import HttpResponse
from micronaut.http.annotation import Body, Controller, Post
from micronaut.validation import Validated

from .Email import Email
# end::imports[]


@Requires(property="spec.name", value="datavalidationpogo")
# tag::clazz[]
@Validated  # <1>
@Controller("/email")
class EmailController:

    @Post("/send")
    def send(self, email: Annotated[Email, Body, Valid]) -> HttpResponse:  # <2>
        return HttpResponse.ok({"msg": "OK"})
# end::clazz[]
