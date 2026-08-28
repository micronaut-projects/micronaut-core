from typing import Annotated

from micronaut.context.annotation import Requires
# tag::imports[]
from jakarta.validation import Valid
from micronaut.http import HttpResponse
from micronaut.http.annotation import Body, Controller, Post
from micronaut.validation import Validated

from .Email import Email
from .FinalValidation import FinalValidation
# end::imports[]


@Requires(property="spec.name", value="datavalidationgroups")
# tag::clazz[]
@Validated  # <1>
@Controller("/email")
class EmailController:

    @Post("/createDraft")
    def createDraft(self, email: Annotated[Email, Body, Valid]) -> HttpResponse:  # <2>
        return HttpResponse.ok({"msg": "OK"})

    @Post("/send")
    @Validated(groups=[FinalValidation])  # <3>
    def send(self, email: Annotated[Email, Body, Valid]) -> HttpResponse:  # <4>
        return HttpResponse.ok({"msg": "OK"})
# end::clazz[]
