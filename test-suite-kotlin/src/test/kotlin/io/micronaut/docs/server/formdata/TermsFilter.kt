package io.micronaut.docs.server.formdata

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.form.FormData
// end::imports[]

@Requires(property = "spec.name", value = "ProfileControllerTest")
// tag::class[]
@ServerFilter("/signup")
class TermsFilter {

    @RequestFilter
    fun requireTerms(form: FormData): HttpResponse<*>? { // <1>
        if (!form.getBoolean("terms", false)) { // <2>
            return HttpResponse.badRequest("The terms must be accepted")
        }
        return null // <3>
    }
}
// end::class[]
