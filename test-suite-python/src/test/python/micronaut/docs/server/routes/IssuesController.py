from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.http.annotation import Controller, Get
# end::imports[]


@Requires(property="spec.name", value="IssuesControllerTest")
# tag::startclass[]
@Controller("/issues")  # <1>
class IssuesController:
# end::startclass[]

    # tag::normal[]
    @Get("/{number}")  # <2>
    def issue(self, number: int) -> str:  # <3>
        return f"Issue # {number}!"  # <4>

    @Get("/issue/{number}")
    def issue_from_id(self, number: int) -> str:  # <5>
        return f"Issue # {number}!"
    # end::normal[]

    # tag::defaultvalue[]
    @Get("/default{/number}")  # <1>
    def issue_from_id_or_default(self, number: int = 0) -> str:  # <2>
        return f"Issue # {number}!"
    # end::defaultvalue[]

# tag::endclass[]
# end::endclass[]
