from micronaut.context.annotation import Requires

# tag::imports[]
from micronaut.http import HttpResponse, HttpStatus, PathVariables
from micronaut.http.annotation import RequestFilter, ServerFilter
# end::imports[]


@Requires(property="spec.name", value="ItemControllerSpec")
# tag::class[]
@ServerFilter("/items/**")  # <1>
class ArchivedItemFilter:
    ARCHIVED = {7}

    @RequestFilter
    def archived(self, pathVariables: PathVariables) -> HttpResponse | None:  # <2>
        item_id = pathVariables.findLong("id")  # <3>
        if item_id.isPresent() and item_id.getAsLong() in self.ARCHIVED:
            return HttpResponse.status(HttpStatus.GONE)  # <4>
        return None
# end::class[]
