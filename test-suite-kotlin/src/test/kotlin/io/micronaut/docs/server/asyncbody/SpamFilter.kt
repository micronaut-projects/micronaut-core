package io.micronaut.docs.server.asyncbody

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.body.AsyncRequestBody
import java.util.concurrent.CompletionStage
// end::imports[]

@Requires(property = "spec.name", value = "PeopleControllerTest")
// tag::class[]
@ServerFilter("/messages")
class SpamFilter {

    @RequestFilter
    fun rejectSpam(body: AsyncRequestBody): CompletionStage<@Nullable HttpResponse<*>?> { // <1>
        return body.copy().text() // <2>
            .thenApply<HttpResponse<*>?> { text ->
                if (text.contains("spam")) HttpResponse.badRequest("Spam is not accepted") // <3>
                else null // <4>
            }
    }
}
// end::class[]
