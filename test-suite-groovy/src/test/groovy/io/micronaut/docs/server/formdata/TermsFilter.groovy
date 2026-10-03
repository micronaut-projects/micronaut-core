package io.micronaut.docs.server.formdata

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.form.FormData
// end::imports[]

@Requires(property = "spec.name", value = "ProfileControllerSpec")
// tag::class[]
@ServerFilter("/signup")
class TermsFilter {

    @RequestFilter
    @Nullable
    HttpResponse<?> requireTerms(FormData form) { // <1>
        if (!form.getBoolean("terms", false)) { // <2>
            return HttpResponse.badRequest("The terms must be accepted")
        }
        return null // <3>
    }
}
// end::class[]
