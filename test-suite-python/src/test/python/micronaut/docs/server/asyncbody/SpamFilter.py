from micronaut.context.annotation import Requires

# tag::imports[]
import java
from micronaut.http import HttpResponse
from micronaut.http.annotation import RequestFilter, ServerFilter
from micronaut.http.body import AsyncRequestBody

CompletionStage = java.type("java.util.concurrent.CompletionStage")
# end::imports[]


@Requires(property="spec.name", value="PeopleControllerSpec")
# tag::class[]
@ServerFilter("/messages")
class SpamFilter:

    @RequestFilter
    def rejectSpam(self, body: AsyncRequestBody) -> CompletionStage[HttpResponse | None]:  # <1>
        return (body.copy().text()  # <2>
                .thenApply(lambda text: HttpResponse.badRequest("Spam is not accepted")  # <3>
                           if "spam" in text
                           else None))  # <4>
# end::class[]
