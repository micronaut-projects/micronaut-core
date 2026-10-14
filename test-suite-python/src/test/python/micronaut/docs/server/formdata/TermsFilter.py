from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.http import HttpResponse
from micronaut.http.annotation import RequestFilter, ServerFilter
from micronaut.http.form import FormData
# end::imports[]


@Requires(property="spec.name", value="ProfileControllerSpec")
# tag::class[]
@ServerFilter("/signup")
class TermsFilter:

    @RequestFilter
    def requireTerms(self, form: FormData) -> HttpResponse | None:  # <1>
        if not form.getBoolean("terms", False):  # <2>
            return HttpResponse.badRequest("The terms must be accepted")
        return None  # <3>
# end::class[]
