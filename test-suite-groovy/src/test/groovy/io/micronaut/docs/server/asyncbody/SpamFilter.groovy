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

@Requires(property = "spec.name", value = "PeopleControllerSpec")
// tag::class[]
@ServerFilter("/messages")
class SpamFilter {

    @RequestFilter
    CompletionStage<@Nullable HttpResponse<?>> rejectSpam(AsyncRequestBody body) { // <1>
        return body.copy().text() // <2>
            .thenApply { String text ->
                text.contains("spam")
                    ? HttpResponse.badRequest("Spam is not accepted") // <3>
                    : null // <4>
            }
    }
}
// end::class[]
